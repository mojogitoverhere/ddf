/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/

const Backbone = require("backbone");
require("backboneassociations");
const announcement = require("component/announcement");
const Common = require("js/Common");
const _ = require("underscore");
const announceDownload = _.debounce(() => {
  announcement.announce({
    title: "Download(s) Queued",
    type: "info",
    message: `File(s) added to download queue.  Please click the spinner in the main navigation bar for information on currently queued downloads.`
  });
}, 200);
const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;

const Download = Backbone.AssociatedModel.extend({
  defaults() {
    return {
      filename: "",
      callback: undefined,
      id: Common.generateUUID()
    };
  }
});

let timeoutId = undefined;

// autoDownload means saved files are set to go to a predefined location, so no dialogs
let Downloads = new (Backbone.AssociatedModel.extend({
  defaults() {
    return {
      processing: undefined,
      queue: [],
      autoDownload: true
    };
  },
  relations: [
    {
      type: Backbone.Many,
      key: "queue",
      relatedModel: Download
    }
  ],
  initialize() {
    this.listenTo(this.get("queue"), "add", announceDownload);
    this.listenTo(this.get("queue"), "add", this.processQueue);
    this.listenTo(this, "change:processing", this.processQueue);
  },
  addToQueue(filename, callback) {
    this.get("queue").add({ filename, callback });
  },
  initiateDownload() {
    const next = this.get("queue").shift();
    if (next) {
      this.set("processing", next.get("filename"));
      next.get("callback")();
    }
  },
  processQueue() {
    const processing = this.get("processing");
    if (processing === undefined) {
      this.initiateDownload();
    }
  },
  finishDownload() {
    clearTimeout(timeoutId);
    window.onblur = undefined;
    window.onfocus = undefined;
    this.set({
      processing: undefined,
      autoDownload: true
    });
  }
}))();

const onBlur = () => {
  Downloads.set("autoDownload", false);
};

const onFocus = () => {
  Downloads.finishDownload();
}

const afterDownload = () => {
  clearTimeout(timeoutId)
  timeoutId = setTimeout(() => {
    if (Downloads.get("autoDownload")) {
      Downloads.finishDownload();
    }
  }, 1000);
};

const fromData = (filename, type, data) => {
  var blob = new Blob([data], { type: type }, filename);
  if (window.navigator.msSaveOrOpenBlob) {
    window.navigator.msSaveBlob(blob, filename);
  } else {
    var elem = window.document.createElement("a");
    elem.href = window.URL.createObjectURL(blob);
    elem.download = filename;
    document.body.appendChild(elem);
    elem.click();
    window.URL.revokeObjectURL(elem.href);
    document.body.removeChild(elem);
  }
  afterDownload();
};

const getFilenameFromResponse = (xhr, filename) => {
  const disposition = xhr.getResponseHeader('Content-Disposition');
  if (disposition && disposition.indexOf('inline') !== -1) {
      var matches = filenameRegex.exec(disposition);
      if (matches != null && matches[1]) { 
        filename = matches[1].replace(/['"]/g, '');
      }
  }
  return filename
}

const fromUrl = (filename, url) => {
  var xhr = new XMLHttpRequest();
  xhr.open("GET", url, true);
  xhr.responseType = "blob";
  xhr.onerror = function(e) {
    announcement.announce({
      title: "Download failed",
      message: "Download failed",
      type: "error"
    });
    afterDownload();
  };
  xhr.onload = function(e) {
    filename = getFilenameFromResponse(e.currentTarget, filename)
    if (this.status == 200) {
      // get binary data as a response
      var blob = this.response;
      window.onblur = onBlur;
      window.onfocus = onFocus;
      fromData(filename, undefined, blob);
    } else {
      afterDownload();
    }
  };

  xhr.send();
};

module.exports = {
  fromData: function(filename, type, data) {
    Downloads.addToQueue(filename, fromData.bind(null, filename, type, data));
  },
  fromUrl: function(filename, url) {
    Downloads.addToQueue(filename, fromUrl.bind(null, filename, url));
  },
  getDownloads() {
    return Downloads;
  }
};
