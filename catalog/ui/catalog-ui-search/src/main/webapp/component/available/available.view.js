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
var Marionette = require("marionette");
var _ = require("underscore");

function getMaxIndex(selectionInterface) {
  const selectedResults = selectionInterface.getSelected();
  const completeResults = selectionInterface.getAvailable();
  return selectedResults.reduce(function(maxIndex, result) {
    return Math.max(maxIndex, completeResults.indexOf(result));
  }, -1);
}

function getMinIndex(selectionInterface) {
  const selectedResults = selectionInterface.getSelected();
  const completeResults = selectionInterface.getAvailable();
  return selectedResults.reduce(function(minIndex, result) {
    return Math.min(minIndex, completeResults.indexOf(result));
  }, completeResults.length);
}

/**
 * Meant to be extended by the import / export available components
 * so they can share common functionality
 */

export default Marionette.ItemView.extend({
  getList() {
    return this.model.get("available").toJSON();
  },
  onFirstRender: function() {
    const debouncedRender = _.debounce(this.render.bind(this), 30);
    this.listenTo(
      this.model,
      "reset:selected remove:selected add:selected change:location add:available remove:available reset:available",
      debouncedRender
    );
  },
  events: {
    "mousedown .virtual-table-row": "handleMouseDown"
  },
  handleMouseDown: function(event) {
    event.preventDefault();
  },
  handleClick: function({ event, index, rowData }) {
    const id = rowData.id;
    const alreadySelected = this.model.get("selected").get(id) !== undefined;
    //shift key wins over all else
    if (event.shiftKey) {
      this.handleShiftClick(id, alreadySelected);
    } else if (event.ctrlKey || event.metaKey) {
      this.handleControlClick(id, alreadySelected);
    } else {
      this.model.clearSelected();
      this.handleControlClick(id, alreadySelected);
    }
  },
  handleShiftClick: function(id, alreadySelected) {
    const selectedResults = this.model.getSelected();
    const indexClicked = this.model.getAvailable().indexOfId(id);
    const firstIndex = getMinIndex(this.model);
    const lastIndex = getMaxIndex(this.model);
    if (selectedResults.length === 0) {
      this.handleControlClick(id, alreadySelected);
    } else if (indexClicked <= firstIndex) {
      this.selectBetween(indexClicked, firstIndex);
    } else if (indexClicked >= lastIndex) {
      this.selectBetween(lastIndex, indexClicked + 1);
    } else {
      this.selectBetween(firstIndex, indexClicked + 1);
    }
  },
  selectBetween: function(startIndex, endIndex) {
    this.model.addSelected(
      this.model.getAvailable().slice(startIndex, endIndex)
    );
  },
  handleControlClick: function(id, alreadySelected) {
    if (alreadySelected) {
      this.model.removeSelected(this.model.getAvailable().get(id));
    } else {
      this.model.addSelected(this.model.getAvailable().get(id));
    }
  }
});
