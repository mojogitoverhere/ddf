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
import FileSystem from "../file-system";

var File = Backbone.Model.extend({
  defaults: {
    path: undefined,
    size: undefined
  }
});

var Files = Backbone.Collection.extend({
  model: File
});

var Task = Backbone.Model.extend({
  defaults: {
    name: undefined,
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
      relatedModel: File,
      CollectionType: Files
    },
    {
      type: Backbone.Many,
      key: "selected",
      relatedModel: File
    },
    {
      type: Backbone.Many,
      key: "tasks",
      relatedModel: Task,
      CollectionType: Tasks
    }
  ],
  initialize: function () {
      this.get('tasks').comparator = this.taskSort;
  },
  taskSort: function(a, b) {
    if (a.get('sortOrder') > b.get('sortOrder')) {
      return 1;
    } else if (a.get('sortOrder') < b.get('sortOrder')) {
      return -1;
    } else {
      // the tasks have the same sortOrder so sort by start time
      return a.get('started').localeCompare(b.get('started'));
    }
  },
  setAvailable: function() {
    var self = this;
    $.ajax({
      url: "/search/catalog/internal/resources/import/available",
      type: "GET",
      contentType: "application/json",
      customErrorHandling: true
    }).success(function(data) {
      self.set({ location: data.root });
      self.set({
        available: data.files.map(file => {
          file.id = file.path;
          return file;
        })
      });
    });
  },
  setTasksUrl: "/search/catalog/internal/resources/import/tasks",
  transformTask(task) {
    var state = "";
    var sortOrder = 0;
    var name = task.details.importFile;
    var created = task.details.created;
    var started = task.started;
    var progress = "--";
    var info = task.details.message;

    if (task.total) {
      if (task.current) {
        progress = task.current + " out of " + task.total + " items";
      } else {
        progress = "0 out of " + task.total + " items";
      }
    } else {
      progress = "Unzipping...";
    }
    if (started) {
      started = new Date(started).toString().slice(0, 24);
      if (task.finished) {
        if (task.failed) {
          state = "Failed";
          progress = "--";
          sortOrder = 3;
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
      sortOrder: sortOrder,
      name: name,
      id: task.id,
      created: created,
      started: started,
      progress: progress,
      info: info
    };
  },
  clearCompleted: function() {
      $.ajax({
          url: "/search/catalog/internal/resources/import/tasks",
          type: "DELETE",
          customErrorHandling: true,
          contentType: "application/json"
      });
  }
});
