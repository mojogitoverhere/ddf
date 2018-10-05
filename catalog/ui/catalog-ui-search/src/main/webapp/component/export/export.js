/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
/*global require*/

var Backbone = require("backbone");
var $ = require("jquery");
import FileSystem from '../file-system'

var Search = Backbone.Model.extend({
  defaults: {
    id: undefined,
    workspace: undefined,
    title: undefined,
    cql: undefined,
    updated: undefined
  }
});

var Searches = Backbone.Collection.extend({
  model: Search
});

var Task = Backbone.Model.extend({
  defaults: {
    title: undefined,
    file: undefined,
    started: undefined,
    state: undefined,
    progress: undefined,
    info: undefined,
    created: undefined,
    sortOrder: 0
  }
});

var Tasks = Backbone.Collection.extend({
  model: Task
});

module.exports = FileSystem.extend({
  relations: [
    {
      type: Backbone.Many,
      key: "available",
      relatedModel: Search,
      CollectionType: Searches
    },
    {
      type: Backbone.Many,
      key: "selected",
      relatedModel: Search
    },
    {
      type: Backbone.Many,
      key: "tasks",
      relatedModel: Task,
      CollectionType: Tasks
    }
  ],
  defaults: {
    ...FileSystem.prototype.defaults,
    formatValues: []
  },
  initialize() {
    FileSystem.prototype.initialize.call(this)
    this.setFormatEnumValues()
  },
  setAvailable: function() {
    var self = this;
    $.ajax({
      url: "/search/catalog/internal/workspaces",
      type: "GET",
      contentType: "application/json",
      async: false,
      customErrorHandling: true
    }).success(function(data) {
      self.set("available", {
        id: "0",
        workspace: "FULL CATALOG",
        title: "--",
        cql: "(anyText ILIKE '*')",
        updated: "--"
      });
      data = data.sort(function(a, b) {
        return b["metacard.modified"] - a["metacard.modified"];
      });
      data.forEach(workspace => {
        if (workspace.queries) {
          var queries = workspace.queries.sort(function(a, b) {
            return a.title.toLowerCase() > b.title.toLowerCase();
          });
          queries.forEach(query => {
            var date = new Date(workspace["metacard.modified"])
              .toString()
              .slice(0, 24);
            self.get("available").push({
              id: query.id,
              workspace: workspace.title,
              title: query.title,
              cql: query.cql,
              updated: date
            });
          });
        }
      });
    });
  },
  setFormatEnumValues: function() {
    var self = this;
    $.ajax({
      url: "/search/catalog/internal/resources/export/formats",
      type: "GET",
      contentType: "application/json",
      customErrorHandling: true,
      async: false
    }).success(function(data) {
      data.formats.forEach(format =>
        self.get("formatValues").push({ label: format, value: format })
      );
    });
  },
  setLocation: function() {
    var self = this;
    $.ajax({
      url: "/search/catalog/internal/resources/export/location",
      type: "GET",
      contentType: "application/json",
      customErrorHandling: true,
      async: false
    })
      .success(function(data) {
        self.set("location", data.root);
      })
      .error(function(data) {
        self.set("location", undefined);
      });
  },
  setTasksUrl: "/search/catalog/internal/resources/export/tasks",
  transformTask(task) {
    var state = "";
    var title = task.details.title;
    var progress =
      (task.current ? task.current : "0") +
      " out of " +
      (task.total ? task.total : "?") +
      " items";
    var sortOrder = 0;
    var name = task.details.filename ? task.details.filename : "--";
    var created = task.details.created;
    var started = task.started;
    var info = task.details.message;

    if (started) {
      started = new Date(started).toString().slice(0, 24);
      if (task.finished) {
        if (task.failed) {
          state = "Failed";
          sortOrder = 3;
          progress = "--";
        } else {
          state = "Complete";
          sortOrder = 4;
        }
      } else {
        state = "In Progress...";
        sortOrder = 1;
      }
    } else {
      state = "Queued";
      sortOrder = 2;
      progress = "--";
      started = "--";
    }
    return {
      state: state,
      title: title,
      sortOrder: sortOrder,
      file: name,
      created: created,
      started: started,
      progress: progress,
      info: info
    };
  },
  clearCompleted: function() {
      $.ajax({
          url: "/search/catalog/internal/resources/export/tasks",
          type: "DELETE",
          customErrorHandling: true,
          contentType: "application/json"
      });
  }
});
