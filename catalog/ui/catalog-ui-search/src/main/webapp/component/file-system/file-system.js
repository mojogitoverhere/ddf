/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
/*global require*/

var Backbone = require("backbone");
var $ = require("jquery");

export default Backbone.AssociatedModel.extend({
  defaults: {
    available: [],
    selected: [],
    location: undefined,
    tasks: [],
    showCompleted: true
  },
  initialize: function() {
    this.get("tasks").comparator = "created";
    this.setTasks();
    this.setAvailable();
    this.setLocation();
  },
  getAvailable() {
    return this.get("available");
  },
  getSelected: function() {
    return this.get("selected");
  },
  clearSelected: function() {
    this.getSelected().reset();
  },
  addSelected: function(item) {
    if (item.constructor !== Array) {
      item = [item];
      }
      this.getSelected().reset(item.concat(this.getSelected().models));
  },
  removeSelected: function(item) {
    this.getSelected().remove(item);
  },
  setAvailable() {
    // override
  },
  setLocation() {
    // override
  },
  setTasksPromise: undefined,
  setTasksUrl: '', // override 
  setTasks: function() {
    if (
      this.setTasksPromise !== undefined &&
      this.setTasksPromise.state() === "pending"
    ) {
      return;
    }
    this.setTasksPromise = $.ajax({
      url: this.setTasksUrl,
      type: "GET",
      contentType: "application/json",
      customErrorHandling: true
    }).success(
      function(data) {
        const transformedTasks = data.map(task => this.transformTask(task));
        this.get("tasks").reset(transformedTasks);
      }.bind(this)
    );
  },
  transformTask(task) {
    // override
  }
});
