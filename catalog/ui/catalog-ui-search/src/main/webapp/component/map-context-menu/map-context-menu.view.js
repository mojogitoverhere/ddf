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
var _ = require('underscore')
var Marionette = require('marionette')
var template = require('./map-context-menu.hbs')
var CustomElements = require('js/CustomElements')
var Clipboard = require('clipboard')
var announcement = require('component/announcement')

module.exports = Marionette.LayoutView.extend({
  template: template,
  tagName: CustomElements.register('map-context-menu'),
  className: 'composed-menu',
  events: {
    click: 'triggerClick',
  },
  initialize: function() {
    this.listenTo(
      this.options.mapModel,
      'change:clickLat change:clickLon',
      this.render
    )
  },
  triggerClick: function() {
    this.$el.trigger('closeDropdown.' + CustomElements.getNamespace())
  },
  onRender: function() {
    this.setupClipboards()
    this.repositionDropdown()
    this.handleOffMap()
  },
  handleOffMap: function() {
    this.$el.toggleClass('is-off-map', this.options.mapModel.isOffMap())
  },
  setupClipboards: function() {
    this.destroyClipboards()
    this.copyCoordinatesClipboard = new Clipboard(this.el.querySelector('.interaction-copy-coordinates'))
    this.copyWktClipboard = new Clipboard(this.el.querySelector('.interaction-copy-wkt'))
    this.copyDms = new Clipboard(this.el.querySelector('.interaction-copy-dms'))
    this.attachListenersToClipboards()
  },
  destroyClipboards() {
    if (this.copyCoordinatesClipboard){
      this.copyCoordinatesClipboard.destroy()
      this.copyWktClipboard.destroy()
      this.copyDms.destroy()
    }
  },
  attachListenersToClipboards() {
    this.attachListenersToClipboard(this.copyCoordinatesClipboard)
    this.attachListenersToClipboard(this.copyWktClipboard)
    this.attachListenersToClipboard(this.copyDms)
  },
  attachListenersToClipboard: function(clipboard) {
    clipboard.on('success', function(e) {
      announcement.announce({
        title: 'Copied to clipboard',
        message: e.text,
        type: 'success',
      })
    })
    clipboard.on('error', function(e) {
      announcement.announce({
        title: 'Press Ctrl+C to copy',
        message: e.text,
        type: 'info',
      })
    })
  },
  serializeData: function() {
    var mapModelJSON = this.options.mapModel.toJSON()
    return mapModelJSON
  },
  repositionDropdown: function() {
    this.$el.trigger('repositionDropdown.' + CustomElements.getNamespace())
  },
})
