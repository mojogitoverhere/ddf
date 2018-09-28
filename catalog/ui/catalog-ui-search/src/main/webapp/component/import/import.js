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

var Backbone = require('backbone');
var $ = require('jquery');

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

module.exports = Backbone.AssociatedModel.extend({
    relations: [
        {
            type: Backbone.Many,
            key: 'files',
            relatedModel: File,
            CollectionType: Files
        },
        {
            type: Backbone.Many,
            key: 'selectedFiles',
            relatedModel: File
        },
        {
            type: Backbone.Many,
            key: 'tasks',
            relatedModel: Task,
            CollectionType: Tasks
        }
    ],
    defaults: {
        root: undefined,
        files: [],
        selectedFiles: [],
        tasks: [],
        showCompleted: true
    },
    initialize: function(){
        this.get('tasks').comparator = 'sortOrder';
        this.setFiles();
        this.setTasks();
    },
    getSelectedFiles: function(){
        return this.get('selectedFiles');
    },
    clearSelectedFiles: function(){
        this.getSelectedFiles().reset();
    },
    addSelectedFile: function(file){
        this.getSelectedFiles().add(file);
    },
    removeSelectedFile: function(file){
        this.getSelectedFiles().remove(file);
    },
    setFiles: function() {
        var self = this;
        $.ajax({
            url: "/search/catalog/internal/resources/import/available",
            type: "GET",
            contentType: "application/json",
            customErrorHandling: true
        }).success(function(data) {
            self.set({root: data.root});
            self.set({files: data.files});
        });
    },
    setTasks: function() {
        var self = this;
        $.ajax({
            url: "/search/catalog/internal/resources/import/tasks",
            type: "GET",
            contentType: "application/json",
            customErrorHandling: true
        }).success(function(data) {
            self.set({tasks: []});
            data.forEach(task => self.addTask(task) );
            if (!self.get('showCompleted')) self.set('tasks', self.get('tasks').filter(task => task.get('state') !== "Complete"));
        })
    },
    addTask: function(task) {
        var state = "";
        var sortOrder = 0;
        var name = task.details.importFile;
        var created = task.details.created;
        var started = task.started;
        var progress = "--";
        var info = "";

        if (task.details.message) {
            var info = name + ": " + task.details.message;
        }

        if (task.total) {
            if (task.current) {
                progress = task.current + " out of " + task.total + " items";
            } else {
                progress = "0 out of " + task.total + " items";
            }
        } else {
            progress = "Unzipping..."
        }

        if (started) {
            started = new Date(started).toString().slice(0,24);
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

        this.get('tasks').add({
            state: state,
            sortOrder: sortOrder,
            name: name,
            created: created,
            started: started,
            progress: progress,
            info: info
        });
    }
});

