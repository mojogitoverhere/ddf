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
var Import = require('./import.js');

module.exports = Marionette.LayoutView.extend({
    template: template,
    tagName: CustomElements.register('import'),
    model: new Import(),
    events: {
        'click .import-button': 'handleImport'
    },
    regions: {
        importMenu: '.import-menu',
        importFileList: '.import-file-list',
        importStatusList: '.import-status-list'
    },
    initialize: function(){
        this.listenTo(router, 'change', this.handleRoute);
        this.listenTo(this.model.get('selectedFiles'), 'add remove reset', this.handleSelectionChange);
        this.handleRoute();
    },
    handleRoute: function(){
        if (router.toJSON().name === 'openImport'){
            this.$el.removeClass('is-hidden');
        } else {
            this.$el.addClass('is-hidden');
        }
    },
    handleImport: function(){
        var paths = this.model.getSelectedFiles().map(file => file.get('path'));
        //TODO and backend enpoint to handle this request TIB-749
        $.ajax({
            url: '/search/catalog/internal/resources/import',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(paths)
        });
    },
    onBeforeShow: function(){
        this.importMenu.show(new NavigationView());
        this.importFileList.show(new ImportFilesView({model:this.model}));
        if (!this.model.get('root')) {
            this.$(".import-location").hide();
        }
    },
    handleSelectionChange: function() {
        if (this.model.get('selectedFiles').isEmpty()) {
            this.$(".import-button").addClass('disabled');
        } else {
            this.$(".import-button").removeClass('disabled');
        }
    }
});

