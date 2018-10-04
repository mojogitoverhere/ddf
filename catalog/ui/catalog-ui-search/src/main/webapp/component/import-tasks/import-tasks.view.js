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
const Marionette = require('backbone.marionette')
import * as React from 'react'
  
import ImportTasks from './import-tasks.jsx'
const $ = require('jquery')
const CustomElements = require('js/CustomElements')
var ConfirmationView = require("component/confirmation/confirmation.view");

module.exports = Marionette.ItemView.extend({
    tagName: CustomElements.register("import-tasks"),
    events: {
        "click .info": "getInfo",
        "click .clearCompleted": "clearCompleted"
      },
    template() {
        const tasks = this.model.get('tasks').toJSON()
        return (
            <ImportTasks location={this.model.get('location')} list={tasks} showCompleted={this.model.get('showCompleted')}
                onShowCompletedChange={this.onShowCompletedChange.bind(this)}
            />
        )
    },
    onShowCompletedChange(event) {
        this.model.set('showCompleted', event.target.checked)
    },
    onFirstRender() {
        this.listenTo(this.model, "change:location change:tasks add:tasks remove:tasks reset:tasks change:showCompleted", this.render);
    },
    getInfo: function(e) {
        const info = $(e.target).data('info')
        ConfirmationView.generateConfirmation({
          prompt: info,
          yes: "Okay"
        });
      },
      clearCompleted: function() {
          this.model.clearCompleted();
      }
});