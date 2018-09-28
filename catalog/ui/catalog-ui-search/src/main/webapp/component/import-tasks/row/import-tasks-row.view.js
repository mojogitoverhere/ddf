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
var template = require('./import-tasks-row.hbs');
var Marionette = require('marionette');
var CustomElements = require('js/CustomElements');
var $ = require('jquery');
var ConfirmationView = require('component/confirmation/confirmation.view');

module.exports = Marionette.ItemView.extend({
    className: 'is-tr',
    tagName: CustomElements.register('import-tasks-row'),
    template: template,
    events: {
        'click .info': 'getInfo'
    },
    getInfo: function() {
        ConfirmationView.generateConfirmation({
            prompt: this.model.get('info'),
            yes: 'Okay'
        })
    }
});
