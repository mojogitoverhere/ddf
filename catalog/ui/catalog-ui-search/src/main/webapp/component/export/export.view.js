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
var wreqr = require('wreqr');
var Marionette = require('marionette');
var _ = require('underscore');
var $ = require('jquery');
var template = require('./export.hbs');
var CustomElements = require('js/CustomElements');
var router = require('component/router/router');
var NavigationView = require('component/navigation/export/navigation.export.view');
var ExportSearchView =  require('component/export-search/export-search.view');
var Export = require('./export.js');
var Property = require('component/property/property');
var PropertyView = require('component/property/property.view');
var ExportTasksView =  require('component/export-tasks/export-tasks.view');

module.exports = Marionette.LayoutView.extend({
    template: template,
    model: new Export(),
    tagName: CustomElements.register('export'),
    events: {
        'click .export-button': 'handleExport',
        'click .export-custom-title': 'handleCustom',
        'click .showCompleted': 'handleShowCompleted'
    },
    regions: {
        exportMenu: '.export-menu',
        exportSearchList: '.export-search-list',
        formatInput: '.export-custom-format',
        typeInput: '.export-custom-type',
        exportStatusList: '.export-status-list'
    },
    initialize: function() {
        this.listenTo(router, 'change', this.handleRoute);
        this.listenTo(this.model.get('selectedSearches'), 'add remove reset', this.handleSelectionChange);
        this.handleRoute();
    },
    handleRoute: function() {
        var self = this;
        if (router.toJSON().name === 'openExport') {
            this.$el.removeClass('is-hidden');
            this.model.update();
            if (self.model.get("exportLocation")) {
                $('.export-location').html("(Configured export directory: "  + self.model.get('exportLocation') + ")");
                this.$(".export-location").show();
            } else {
                this.$(".export-location").hide();
            }
            self.timer = setInterval(function() {
                self.model.setTasks();
            }, 500);
        } else {
            self.$el.addClass('is-hidden');
            clearTimeout(self.timer);
        }
    },
    handleCustom: function() {
        this.$el.toggleClass('is-custom');
        this.$el.toggleClass('not-custom');
    },
    onBeforeShow: function() {
        this.exportMenu.show(new NavigationView());
        this.exportSearchList.show(new ExportSearchView({model:this.model}));
        this.formatModel = new Property({
            label: 'Export Format',
            value: ['backup'],
            showLabel: true,
            id: "format",
            isEditing: true,
            enum: this.model.get("formatValues")
        });
        this.formatInput.show(new PropertyView({model: this.formatModel}));
        this.formatInput.currentView.turnOnLimitedWidth();
        this.typeModel = new Property({
            label: 'Export Type',
            value: ['METADATA_AND_CONTENT'],
            showLabel: true,
            id: "type",
            isEditing: true,
            limitedWidth: true,
            enum: [
                {label:"Metadata and Content", value:"METADATA_AND_CONTENT"},
                {label:"Metadata Only", value:"METADATA_ONLY"},
                {label:"Content Only", value:"CONTENT_ONLY"},
            ]
        });
        this.typeInput.show(new PropertyView({model: this.typeModel}));
        this.typeInput.currentView.turnOnLimitedWidth();
        this.exportStatusList.show(new ExportTasksView({model:this.model}));
    },
    handleSelectionChange: function() {
        if (this.model.get('selectedSearches').isEmpty()) {
            this.$(".export-button").addClass('disabled');
        } else {
            this.$(".export-button").removeClass('disabled');
        }
    },
    handleExport: function() {
        var transformerId = this.formatInput.currentView.model.getValue()[0];
        var exportType = this.typeInput.currentView.model.getValue()[0];
        if (transformerId == "backup") {
            transformerId = "xml";
        }
        this.model.getSelectedSearches().forEach(function(search) {
            var workspace = search.get("workspace");
            var title = workspace ? workspace  : workspace + " - " + search.get("title");
            $.ajax({
                url: '/search/catalog/internal/resources/export',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({
                    "cql": search.get("cql"),
                    "metadataFormat": transformerId,
                    "type": exportType,
                    "title": title
                })
            })
        });
    },
    handleShowCompleted: function() {
        this.model.set('showCompleted', !this.model.get('showCompleted'));
    }
});

