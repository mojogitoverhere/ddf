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

module.exports = Backbone.AssociatedModel.extend({
    url: "/search/catalog/internal/resources/import/available",
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
        }
    ],
    defaults: {
        root: '/imports/',
        files: [],
        selectedFiles: []
    },
    initialize: function(){
        var self = this;
        $.ajax({
            url: self.url,
            type: "GET",
            contentType: "application/json"
        }).success(function(data) {
           self.set({root: data.root});
           self.set({files: data.files});
        });
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
});

