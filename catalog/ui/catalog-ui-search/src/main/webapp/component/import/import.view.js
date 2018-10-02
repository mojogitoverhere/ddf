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
var Marionette = require('marionette');
var $ = require('jquery');
var CustomElements = require('js/CustomElements');
var router = require('component/router/router');
var NavigationView = require('component/navigation/import/navigation.import.view');
var ImportAvailableView = require('component/import-available/import-available.view');
var ImportTasksView = require('component/import-tasks/import-tasks.view');
var Import = require('./import.js');
import * as React from 'react'

module.exports = Marionette.LayoutView.extend({
    template() {
        return (
            <React.Fragment>
                <div className="import-menu"></div>
                <div className="import-content">
                    <div className="import-files-container">
                    </div>
                    <div className="import-status-container">
                    </div>
                </div>
            </React.Fragment>
        )
    },
    tagName: CustomElements.register('import'),
    model: new Import(),
    regions: {
        importMenu: '.import-menu',
        importFileList: '.import-files-container',
        importStatusList: '.import-status-container',
    },
    initialize: function () {
        this.listenTo(router, 'change', this.handleRoute);
        this.handleRoute();
    },
    handleRoute: function () {
        if (router.toJSON().name === 'openImport') {
            var self = this;
            self.$el.removeClass('is-hidden');
            self.model.setAvailable();
            this.timer = setInterval(function () {
                self.model.setTasks();
            }, 2000);
        } else {
            this.$el.addClass('is-hidden');
            clearTimeout(this.timer);
        }
    },
    onRender() {
        this.onBeforeShow()
    },
    onBeforeShow: function () {
        this.importMenu.show(new NavigationView());
        this.importFileList.show(new ImportAvailableView({ model: this.model }));
        this.importStatusList.show(new ImportTasksView({ model: this.model }));
    },
    handleRefresh: function () {
        this.model.setAvailable();
    }
});

