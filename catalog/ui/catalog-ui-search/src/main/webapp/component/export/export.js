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
        }
    ],
    defaults: {
        searches: [],
        selectedSearches: [],
        exportLocation: undefined,
        formatValues: []
    },
    initialize: function() {
        this.setExportLocation();
        if (this.get("exportLocation")) {
            this.setSearches();
        }
        this.setFormatEnumValues();
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
                var queries = workspace.queries.sort(function(a,b) {
                    return a.title.toLowerCase() > b.title.toLowerCase();
                });
                queries.forEach( query => {
                    var date = (new Date(workspace['metacard.modified'])).toString().slice(0,24);
                    self.get('searches').push({searchid: query.id, workspace: workspace.title, title: query.title, cql: query.cql, updated: date});
                });
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
        });
    }
});

