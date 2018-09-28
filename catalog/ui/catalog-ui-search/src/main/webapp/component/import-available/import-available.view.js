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
import AvailableView from '../available'
var CustomElements = require("js/CustomElements");
import * as React from "react";
const $ = require('jquery')
import ImportAvailable from './import-available'

module.exports = AvailableView.extend({
  tagName: CustomElements.register("import-available"),
  template: function() {
    return (
        <ImportAvailable 
          location={this.model.get('location')} 
          list={this.getList()} 
          onRowClick={this.handleClick.bind(this)} 
          selectionInterface={this.model} 
            onRefresh={this.handleRefresh.bind(this)}
            onImport={this.handleImport.bind(this)}
          />
    )
  },
  handleRefresh() {
    this.model.setAvailable();
  },
  handleImport() {
        $.ajax({
            url: '/search/catalog/internal/import',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(this.model.getSelected().map(item => item.getLocation())),
            customErrorHandling: true
        })
  }
});
