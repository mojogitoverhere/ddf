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
var $ = require("jquery");
var CustomElements = require("js/CustomElements");
var router = require("component/router/router");
var NavigationView = require("component/navigation/export/navigation.export.view");
var ExportAvailableView = require("component/export-available/export-available.view");
var Export = require("./export.js");
var Property = require("component/property/property");
var PropertyView = require("component/property/property.view");
var ExportTasksView = require("component/export-tasks/export-tasks.view");
var ConfirmationView = require("component/confirmation/confirmation.view");
import * as React from "react";

module.exports = Marionette.LayoutView.extend({
  template() {
    return (
      <React.Fragment>
        <div className="export-menu" />

        <div className="export-content">
          <div className="export-search-container">
            <div className="export-title-container">
              <div className="export-title">Local Content to Export</div>
              <div className="export-custom-title is-button">
                Custom Export Settings
                <span className="fa fa-caret-up is-custom" />
                <span className="fa fa-caret-down not-custom" />
              </div>
            </div>
            <div className="export-custom-container">
              <div className="export-full-fidelity">
                <label><input type="radio" name="export" id="full-fidelity" value="full-fidelity" defaultChecked />Full-Fidelity</label>
                <div className="export-custom-warning">
                  <span className="fa fa-exclamation warning" />
                  Only <b>Full-Fidelity</b> exports are
                  able to be reimported <b>without data loss</b>.
                </div>
              </div>
              <div className="export-custom">
                <label><input type="radio" name="export" id="custom" value="custom" />Custom</label>
                <div className="export-custom-options">
                  <div className="export-custom-type input" />
                  <div className="export-custom-format input" />
                </div>
              </div>
            </div>

            <div className="export-search-list"></div>
          </div>

          <div className="export-status-container">
          </div>
        </div>
      </React.Fragment>
    );
  },
  model: new Export(),
  tagName: CustomElements.register("export"),
  events: {
    "click .export-button": "handleExport",
    "click .export-custom-title": "handleCustom",
    "change input[type=radio]": "handleExportSettings"
  },
  regions: {
    exportMenu: ".export-menu",
    exportSearchList: ".export-search-list",
    formatInput: ".export-custom-format",
    typeInput: ".export-custom-type",
    exportStatusList: ".export-status-container"
  },
  initialize: function() {
    this.listenTo(router, "change", this.handleRoute);
    this.handleRoute();
  },
  handleRoute: function() {
    var self = this;
    if (router.toJSON().name === "openExport") {
      this.$el.removeClass("is-hidden");
      this.model.setAvailable();
      self.timer = setInterval(function() {
        self.model.setTasks();
      }, 2000);
    } else {
      self.$el.addClass("is-hidden");
      clearTimeout(self.timer);
    }
  },
  handleCustom: function() {
    this.$el.toggleClass("is-custom");
    $(window).resize()
  },
  handleExportSettings: function() {
    var isFullFidelity = $("#full-fidelity")[0].checked;
    if (isFullFidelity === true) {
      this.formatModel.set("isDisabled", true);
      this.typeModel.set("isDisabled", true);
    } else {
      this.formatModel.set("isDisabled", false);
      this.typeModel.set("isDisabled", false);
    }
  },
  onBeforeShow: function() {
    this.exportMenu.show(new NavigationView());
    this.exportSearchList.show(new ExportAvailableView({ model: this.model, handleExport: this.handleExport.bind(this) }));
    this.formatModel = new Property({
      label: "Export Format",
      value: ["xml"],
      showLabel: true,
      id: "format",
      isEditing: true,
      isDisabled: true,
      enum: this.model.get("formatValues")
    });
    this.formatInput.show(new PropertyView({ model: this.formatModel }));
    this.formatInput.currentView.turnOnLimitedWidth();
    this.typeModel = new Property({
      label: "Export Type",
      value: ["METADATA_AND_CONTENT"],
      showLabel: true,
      id: "type",
      isEditing: true,
      limitedWidth: true,
      isDisabled: true,
      enum: [
        { label: "Metadata and Content", value: "METADATA_AND_CONTENT" },
        { label: "Metadata Only", value: "METADATA_ONLY" },
        { label: "Content Only", value: "CONTENT_ONLY" }
      ]
    });
    this.listenTo(this.typeModel, "change:value", function() {
      this.formatModel.set("isDisabled", this.typeModel.get("value")[0] === "CONTENT_ONLY");
    });
    this.typeInput.show(new PropertyView({ model: this.typeModel }));
    this.typeInput.currentView.turnOnLimitedWidth();
    this.exportStatusList.show(new ExportTasksView({ model: this.model }));
  },
  doExport: function(transformerId, exportType) {
    const searches = this.model.getSelected().map(function(search) {
      var workspace = search.get("workspace");
      var title = workspace === "FULL CATALOG" ? workspace : workspace + " - " + search.get("title");
      return {
          cql: search.get("cql"),
          metadataFormat: transformerId,
          type: exportType,
          title: title
        }
    });
    if (searches.length === 0) {
            return;
    }
    $.ajax({
      url: "/search/catalog/internal/resources/export",
      type: "POST",
      contentType: "application/json",
      data: JSON.stringify(searches)
    });
  },
  handleExport: function() {
    var transformerId = this.formatInput.currentView.model.getValue()[0];
    var exportType = this.typeInput.currentView.model.getValue()[0];
    var isFullFidelity = $("#full-fidelity")[0].checked;
    if (isFullFidelity === true) {
      transformerId = "xml";
      exportType = "METADATA_AND_CONTENT";
    }
    if (!(transformerId === "xml" && exportType === "METADATA_AND_CONTENT")) {
      this.listenTo(ConfirmationView.generateConfirmation({
              prompt: 'Only full-fidelity exports are able to be reimported without any data loss. Are you sure you want to export using a custom format?',
              no: 'Cancel',
              yes: 'Export'
          }),
          'change:choice',
          function(confirmation) {
            if (confirmation.get('choice')) {
              this.doExport(transformerId, exportType);
            }
          }.bind(this));
    } else {
      this.doExport(transformerId, exportType);
    }
  },
});
