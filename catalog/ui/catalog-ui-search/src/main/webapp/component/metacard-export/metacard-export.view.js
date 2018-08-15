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
/*global define, setTimeout*/
define([
    'marionette',
    'underscore',
    'jquery',
    './metacard-export.hbs',
    'component/property/property',
    'component/property/property.view',
    'js/CustomElements',
    'js/store',
    'js/Download',
    'properties.js'
], function (Marionette, _, $, template, Property, PropertyView, CustomElements, store, Download, properties) {

    return Marionette.LayoutView.extend({
        setDefaultModel: function() {
            this.model = this.selectionInterface.getSelectedResults();
        },
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
            if (!options.model){
                this.setDefaultModel();
            }
        },
        onBeforeShow: function() {
            this.formatModel = new Property({
                label: 'Format',
                enumFiltering: true,
                showLabel: false,
                id: "formats",
                isEditing: true,
                enum: this.getEnumValues()
            });
            this.formatInput.show(new PropertyView({model: this.formatModel}));
        },
        triggerExport: function() {
            var transformer = this.formatInput.currentView.model.getValue()[0];
            this.model.forEach(function(result) {
                var filename = transformer + "-metadata-" + result.get('metacard').get('properties').get('title');
                var url = result.get('metacard').get('properties').get('resource-download-url');
                url = url.replace('resource', transformer);
                Download.fromUrl(filename, url);
            })
            this.closeLightbox();
        },
        getEnumValues: function() {
            var enumValues = new Array();
            properties.exportFormats.forEach(function(format) {
                var val = {label:format, value:format};
                enumValues.push(val);
            });
            return enumValues;
        },
        closeLightbox: function() {
            this.$el.trigger(CustomElements.getNamespace() + 'close-lightbox');
        }
    });
});