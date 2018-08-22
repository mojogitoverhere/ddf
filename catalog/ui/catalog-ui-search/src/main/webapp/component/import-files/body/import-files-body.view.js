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
var CustomElements = require('js/CustomElements');
var RowView = require('../row/import-files-row.view');
var EmptyView = require('../empty/import-files-empty.view');
var $ = require('jquery');

module.exports = Marionette.CollectionView.extend({
    tagName: CustomElements.register('import-files-body'),
    className: 'is-tbody is-list has-list-highlighting',
    events: {
        'click > *': 'handleClick',
        'mousedown > *': 'handleMouseDown',
        'click a': 'handleLinkClick'
    },
    childView: RowView,
    childViewOptions: function() {
        return {
            selectionInterface: this.options.selectionInterface
        }
    },
    emptyViewOptions: function() {
        return {
            model: this.options.selectionInterface
        }
    },
    getEmptyView: function() {
        this.$el.removeClass("is-list");
        this.$el.removeClass("has-list-highlighting");
        return EmptyView;
    },
    handleLinkClick: function(event) {
        event.stopPropagation();
    },
    handleMouseDown: function(event) {
        event.preventDefault();
    },
    handleClick: function(event) {
        var files = this.$el.children();
        var indexClicked = files.index(event.currentTarget);
        var filepath = event.currentTarget.getAttribute('data-filepath');
        var alreadySelected = $(event.currentTarget).hasClass('is-selected');
        //shift key wins over all else
        if (event.shiftKey) {
            this.handleShiftClick(filepath, indexClicked, alreadySelected);
        } else if (event.ctrlKey || event.metaKey) {
            this.handleControlClick(filepath, alreadySelected);
        } else {
            this.options.selectionInterface.clearSelectedFiles();
            this.handleControlClick(filepath, alreadySelected);
        }
    },
    handleShiftClick: function(filepath, indexClicked, alreadySelected) {
        var files = this.$el.children();
        var selectedFiles = this.$el.children('.is-selected');
        var firstIndex = files.index(selectedFiles.first());
        var lastIndex = files.index(selectedFiles.last());
        if (firstIndex === -1 && lastIndex === -1) {
            //this.options.selectionInterface.clearSelectedResults();
            this.handleControlClick(filepath, alreadySelected);
        } else if (indexClicked <= firstIndex) {
            this.selectBetween(indexClicked, firstIndex);
        } else if (indexClicked >= lastIndex) {
            this.selectBetween(lastIndex, indexClicked + 1);
        } else {
            this.selectBetween(firstIndex, indexClicked + 1);
        }
    },
    selectBetween: function(startIndex, endIndex) {
        this.options.selectionInterface.addSelectedFile(this.options.selectionInterface.get('files').filter(function(test, index) {
            return (index >= startIndex) && (index < endIndex);
        }));
    },
   handleControlClick: function(filepath, alreadySelected) {
        if (alreadySelected) {
            this.options.selectionInterface.removeSelectedFile(this.options.selectionInterface.get('files').findWhere({path: filepath}));
        } else {
            this.options.selectionInterface.addSelectedFile(this.options.selectionInterface.get('files').findWhere({path: filepath}));
        }
    }
});