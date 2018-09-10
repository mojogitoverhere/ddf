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
var template = require('./export-search-row.hbs');
var Marionette = require('marionette');
var CustomElements = require('js/CustomElements');

module.exports = Marionette.ItemView.extend({
    className: 'is-tr',
    tagName: CustomElements.register('export-search-row'),
    template: template,
    attributes: function() {
        return {
            'data-searchid': this.serializeData().searchid
        };
    },
    serializeData: function() {
        return this.model.toJSON();
    },
    initialize: function(options) {
        if (!options.selectionInterface) {
            throw 'Selection interface has not been provided';
        }
        this.listenTo(this.options.selectionInterface.getSelectedSearches(), 'update add remove reset', this.handleSelectionChange);
        this.handleSelectionChange();
    },
    handleSelectionChange: function() {
        var selectedSearches = this.options.selectionInterface.get('selectedSearches');
        var isSelected = selectedSearches.findWhere({searchid: this.serializeData().searchid});
        this.$el.toggleClass('is-selected', Boolean(isSelected));
    }
});
