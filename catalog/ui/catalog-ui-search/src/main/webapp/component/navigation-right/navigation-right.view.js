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
var CustomElements = require('CustomElements');
var template = require('./navigation-right.hbs');
var HelpView = require('component/help/help.view');
var UserSettings = require('component/user-settings/user-settings.view');
var UserNotifications = require('component/user-notifications/user-notifications.view');
var UserView = require('component/user/user.view');
var SlideoutViewInstance = require('component/singletons/slideout.view-instance.js');
var SlideoutRightViewInstance = require('component/singletons/slideout.right.view-instance.js');
var user = require('component/singletons/user-instance');
var notifications = require('component/singletons/user-notifications');
const Download = require('js/Download')
const DownloadsView = require('component/downloads/downloads.view')

module.exports = Marionette.ItemView.extend({
    template: template,
    tagName: CustomElements.register('navigation-right'),
    model: user,
    events: {
        'click .item-downloads': 'toggleDownloads',
        'click .item-help': 'toggleHelp',
        'click .item-settings': 'toggleUserSettings',
        'click .item-alerts': 'toggleAlerts',
        'click .item-user': 'toggleUser',
        'mousedown .item-help': 'preventPropagation'
    },
    onRender: function(){
        this.handleUser();
        this.handleUnseenNotifications();
        this.handleDownloads();
        this.listenTo(notifications, 'change add remove reset update', this.handleUnseenNotifications);
        this.listenTo(Download.getDownloads(), 'change:processing', this.handleDownloads);
    },
    handleDownloads() {
        this.$el.toggleClass('has-downloads', Download.getDownloads().get('processing') !== undefined)
    },
    handleUser: function(){
        this.$el.toggleClass('is-guest', this.model.get('user').isGuestUser());
    },
    toggleDownloads() {
        SlideoutRightViewInstance.updateContent(new DownloadsView({
            model: Download.getDownloads()
        }));
        SlideoutRightViewInstance.open();
    },
    toggleAlerts: function(e){
        SlideoutRightViewInstance.updateContent(new UserNotifications());
        SlideoutRightViewInstance.open();
    },
    toggleHelp: function(e) {
        HelpView.toggleHints();
    },
    toggleUserSettings: function() {
        SlideoutViewInstance.updateContent(new UserSettings());
        SlideoutViewInstance.open();
    },
    toggleUser: function(){
        SlideoutRightViewInstance.updateContent(new UserView());
        SlideoutRightViewInstance.open();
    },
    handleUnseenNotifications: function(){
        this.$el.toggleClass('has-unseen-notifications', notifications.hasUnseen());
    },
    preventPropagation: function(e) {
        e.stopPropagation();
    }
});