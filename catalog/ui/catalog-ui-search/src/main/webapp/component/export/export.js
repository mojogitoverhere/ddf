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
var properties = require('properties')

var Search = Backbone.Model.extend({
    defaults: {
        searchid: undefined,
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

module.exports = Backbone.AssociatedModel.extend({
    relations: [
        {
            type: Backbone.Many,
            key: 'searches',
            relatedModel: Search,
            CollectionType: Searches

        },
        {
            type: Backbone.Many,
            key: 'selectedSearches',
            relatedModel: Search
        },
        {
            type: Backbone.Many,
            key: 'tasks',
            relatedModel: Task,
            CollectionType: Tasks
        }
    ],
    defaults: {
        searches: [],
        selectedSearches: [],
        exportLocation: undefined,
        formatValues: [],
        tasks: [],
        showCompleted: true
    },
    initialize: function() {
        this.update();
        this.get('tasks').comparator = 'sortOrder';
        this.setTasks();
    },
    getSelectedSearches: function() {
        return this.get('selectedSearches');
    },
    clearSelectedSearches: function() {
        this.getSelectedSearches().reset();
    },
    addSelectedSearch: function(search) {
        this.getSelectedSearches().add(search);
    },
    removeSelectedSearch: function(search) {
        this.getSelectedSearches().remove(search);
    },
    setSearches: function() {
        var self = this;
        $.ajax({
            url: "/search/catalog/internal/workspaces",
            type: "GET",
            contentType: "application/json",
            async: false,
            customErrorHandling: true
        }).success(function(data) {
            self.set('searches', {searchid: "0", workspace: "FULL CATALOG", title: "--", cql: "(anyText ILIKE '*')", updated: "--"});
            data = data.sort(function(a,b) {
                return b['metacard.modified'] - a['metacard.modified'];
            });
            data.forEach( workspace => {
                if (workspace.queries) {
                    var queries = workspace.queries.sort(function (a, b) {
                        return a.title.toLowerCase() > b.title.toLowerCase();
                    });
                    queries.forEach(query => {
                        var date = (new Date(workspace['metacard.modified'])).toString().slice(0, 24);
                        self.get('searches').push({
                            searchid: query.id,
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
        self.get('formatValues').push({label: "Backup Format", value: "backup"});
        $.ajax({
            url: "/search/catalog/internal/resources/export/formats",
            type: "GET",
            contentType: "application/json",
            customErrorHandling: true,
            async: false
        }).success(function (data) {
            data.formats.forEach(format => self.get('formatValues').push({label: format, value: format}));
        });
    },
    setExportLocation: function() {
        var self = this;
        $.ajax({
            url: "/search/catalog/internal/resources/export/location",
            type: "GET",
            contentType: "application/json",
            customErrorHandling: true,
            async: false
        }).success(function (data) {
            self.set("exportLocation", data.root);
        }).error(function (data) {
            self.set("exportLocation", undefined);
        });
    },
    setTasks: function() {
        var self = this;
        $.ajax({
            url: "/search/catalog/internal/resources/export/tasks",
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
        var title = task.details.title;
        var progress = (task.current ? task.current : "0") + " out of " + (task.total ? task.total : "?") + " items";
        var sortOrder = 0;
        var name = task.details.filename ? task.details.filename : "--";
        var created = task.details.created;
        var started = task.started;
        var info = task.details.message;

        if (started) {
            started = new Date(started).toString().slice(0,24);
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
        this.get('tasks').add({
            state: state,
            title: title,
            sortOrder: sortOrder,
            file: name,
            created: created,
            started: started,
            progress: progress,
            info: info
        });
    },
    update: function() {
        this.setFormatEnumValues();
        this.setExportLocation();
        if (this.get("exportLocation")) {
            this.setSearches();
        } else {
            this.set("searches", []);
        }
    }
});

