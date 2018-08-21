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
/*global require*/
var _ = require('underscore');
var $ = require('jquery');
var Marionette = require('marionette');
var CustomElements = require('js/CustomElements');
var properties = require('properties');
var store = require('js/store');
var Download = require('js/Download');
var template = require('./metacard-export.hbs');
var Property = require('component/property/property');
var PropertyView = require('component/property/property.view');

module.exports = Marionette.LayoutView.extend({
    template: template,
    tagName: CustomElements.register('metacard-export'),
    regions: {
        formatInput: '.export-format-options-dropdown'
    },
    events: {
        'click button.export-confirm': 'triggerExport',
        'click button.export-cancel': 'closeLightbox'
    },
    selectionInterface: store,
    initialize: function(options) {
        this.selectionInterface = options.selectionInterface || this.selectionInterface;
        if (!options.model) {
            this.model = this.selectionInterface.getSelectedResults();
        }
    },
    onBeforeShow: function() {
        this.formatModel = new Property({
            label: 'Format',
            value: ['catalog.data.metacard.metadata.xml'],
            enumFiltering: true,
            showLabel: false,
            id: "formats",
            isEditing: true,
            enum: this.getEnumValues()
        });
        this.formatInput.show(new PropertyView({model: this.formatModel}));
    },
    triggerExport: function() {
        var transformerId = this.formatInput.currentView.model.getValue()[0];
        this.model.forEach(function(result) {
            var filename = result.get('metacard').get('properties').get('id') + "." + transformerId.split(".").pop();
            var url = _.find(result.get('actions'), {id: transformerId}).url;
            Download.fromUrl(filename, url);
        })
        this.closeLightbox();
    },
    getEnumValues: function() {
        return this.getCommonActions().filter(actionId => !this.match(properties.exportActionBlacklist, actionId))
            .sort()
            .map(actionId => ({label:actionId.split(".").pop(), value:actionId}));
    },
    getCommonActions: function() {
        var actionIds = [];
        this.model.forEach(function(result) {
            actionIds.push(result.get('actions').map(action => action.id));
        });
        return actionIds.reduce((x, y) => _.intersection(x, y));
    },
    match: function(regexList, action) {
        return _.chain(regexList)
            .map(str => (new RegExp(str)))
            .find(regex => regex.exec(action))
            .value() !== undefined;
    },
    closeLightbox: function() {
        this.$el.trigger(CustomElements.getNamespace() + 'close-lightbox');
    }
});
