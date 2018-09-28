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
var template = require('./import.hbs');
var CustomElements = require('js/CustomElements');
var router = require('component/router/router');
var NavigationView = require('component/navigation/import/navigation.import.view');
var ImportFilesView =  require('component/import-files/import-files.view');
var ImportTasksView =  require('component/import-tasks/import-tasks.view');
var Import = require('./import.js');

module.exports = Marionette.LayoutView.extend({
    template: template,
    tagName: CustomElements.register('import'),
    model: new Import(),
    events: {
        'click .import-button': 'handleImport',
        'click .showCompleted': 'handleShowCompleted',
        'click .refresh': 'handleRefresh',
    },
    regions: {
        importMenu: '.import-menu',
        importFileList: '.import-file-list',
        importStatusList: '.import-status-list',
    },
    initialize: function(){
        this.listenTo(router, 'change', this.handleRoute);
        this.listenTo(this.model.get('selectedFiles'), 'add remove reset', this.handleSelectionChange);
        this.listenTo(this.model, "change:root", this.handleRootChange, this);
        this.handleRoute();
    },
    handleRoute: function(){
        if (router.toJSON().name === 'openImport'){
            var self = this;
            self.$el.removeClass('is-hidden');
            self.model.setFiles();
            this.timer = setInterval(function() {
                self.model.setTasks();
            }, 500);
        } else {
            this.$el.addClass('is-hidden');
            clearTimeout(this.timer);
        }
    },
    handleImport: function(){
        this.model.getSelectedFiles().forEach( file =>
            $.ajax({
                url: '/search/catalog/internal/import',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(file),
                customErrorHandling: true
            })
        );
    },
    onBeforeShow: function(){
        this.importMenu.show(new NavigationView());
        this.importFileList.show(new ImportFilesView({model:this.model}));
        this.importStatusList.show(new ImportTasksView({model:this.model}));
    },
    handleSelectionChange: function() {
        if (this.model.get('selectedFiles').isEmpty()) {
            this.$(".import-button").addClass('disabled');
        } else {
            this.$(".import-button").removeClass('disabled');
        }
    },
    handleRootChange: function() {
        if (!this.model.get('root')) {
            this.$(".import-location").hide();
        } else {
            this.$(".import-location").show();
        }
        $('.import-location').html("(Configured import directory: "  + this.model.get('root') + ")");
    },
    handleShowCompleted: function() {
        this.model.set('showCompleted', !this.model.get('showCompleted'));
    },
    handleRefresh: function() {
        this.model.setFiles();
    }
});

