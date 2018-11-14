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
var template = require('./map-hover.hbs')
var CustomElements = require('js/CustomElements')
const ResultItemView = require('component/result-item/result-item.view')

module.exports = Marionette.LayoutView.extend({
  template: template,
  tagName: CustomElements.register('map-hover'),
  regions: {
    resultItem: '> .result-item'
  },
  initialize: function() {
    this.listenTo(
      this.options.mapModel,
      'change:targetMetacard',
      this.render
    )
  },
  onRender: function() {
    this.setupResultItem()
  },
  setupResultItem() {
    const targetMetacard = this.options.mapModel.get('targetMetacard')
    if (targetMetacard) {
      this.resultItem.show(new ResultItemView({
        model: targetMetacard,
        selectionInterface: this.options.selectionInterface,
        hideInteractions: true
      }))
    } else {
      this.resultItem.empty()
    }
  },
})
