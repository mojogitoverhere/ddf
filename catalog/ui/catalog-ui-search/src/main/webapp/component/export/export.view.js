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

module.exports = Marionette.LayoutView.extend({
    template: template,
    tagName: CustomElements.register('export'),
    events: {
        'click .export-button': 'handleExport'
    },
    regions: {
        exportMenu: '.export-menu'
    },
    initialize: function(){
        this.listenTo(router, 'change', this.handleRoute);
        this.handleRoute();
    },
    handleRoute: function(){
        if (router.toJSON().name === 'openExport'){
            this.$el.removeClass('is-hidden');
        } else {
            this.$el.addClass('is-hidden');
        }
    },
    handleExport: function(){

    },
    onBeforeShow: function(){
        this.exportMenu.show(new NavigationView());
    },
    handleSelectionChange: function() {

    }
});

