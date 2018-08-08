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
/*global define, setTimeout*/
define([
    'marionette',
    'underscore',
    'jquery',
    './metacard-delete.hbs',
    'js/CustomElements',
    'js/store',
    'component/loading/loading.view',
    'component/loading-companion/loading-companion.view',
    'component/confirmation/confirmation.view',
    'js/ResultUtils',
    'component/announcement',
    'js/jquery.whenAll'
], function(Marionette, _, $, template, CustomElements, store, LoadingView, LoadingCompanionView, ConfirmationView, ResultUtils, announcement) {

    return Marionette.ItemView.extend({
        setDefaultModel: function() {
            this.model = this.selectionInterface.getSelectedResults();
        },
        template: template,
        tagName: CustomElements.register('metacard-delete'),
        events: {
            'click button.delete': 'handleDelete',
            'click button.perm-delete': 'handlePermDelete',
            'click button.cancel': 'handleCancel'
        },
        ui: {},
        orphans: [],
        selectionInterface: store,
        initialize: function(options) {
            this.orphans = [];
            this.selectionInterface = options.selectionInterface || this.selectionInterface;
            if (!options.model) {
                this.setDefaultModel();
            }
            this.fetchOrphans();
            this.listenTo(this.model, 'refreshdata', this.handleTypes);
            this.handleTypes();
            this.toggleDeleteLayout();
        },
        handleTypes: function() {
            var types = {};
            this.model.forEach(function(result) {
                var tags = result.get('metacard').get('properties').get('metacard-tags');
                if (result.isWorkspace()) {
                    types.workspace = true;
                } else if (result.isResource()) {
                    types.resource = true;
                } else if (result.isRevision()) {
                    types.revision = true;
                } else if (result.isDeleted()) {
                    types.deleted = true;
                }
                if (result.isRemote()){
                    types.remote = true;
                }
            });
            this.$el.toggleClass('is-mixed', Object.keys(types).length > 1);
            this.$el.toggleClass('is-workspace', types.workspace !== undefined);
            this.$el.toggleClass('is-resource', types.resource !== undefined);
            this.$el.toggleClass('is-revision', types.revision !== undefined);
            this.$el.toggleClass('is-deleted', types.deleted !== undefined);
            this.$el.toggleClass('is-remote', types.remote !== undefined);
        },
        toggleDeleteLayout: function() {
            var isPermDelete = this.options.permanent === true;
            this.$el.toggleClass('is-perm-delete', isPermDelete);
            this.$el.toggleClass('is-delete', !isPermDelete);
        },
        handleCancel: function() {
            this.closeLightbox();
        },
        handleDelete: function() {
            this.deleteById(this.getMetacardIds());
        },
        deleteById: function(ids) {
            var loadingView = new LoadingView();
            $.ajax({
                url: '/search/catalog/internal/metacards',
                type: 'DELETE',
                data: JSON.stringify(ids),
                contentType: 'application/json'
            }).then(function(response){
                //needed for high latency systems where refreshResults might take too long
                this.model.forEach(function(result){
                    result.get('metacard').get('properties').set('metacard-tags', ['deleted']);
                    result.trigger('refreshdata');
                });
                this.refreshResults();
                this.closeLightbox();
            }.bind(this)).always(function(response) {
                setTimeout(function() { //let solr flush
                    loadingView.remove();
                }, 2000);
            }.bind(this));
        },
        handlePermDelete: function() {
            this.permDeleteById(this.getMetacardIds());
        },
        permDeleteById: function(ids) {
            var loadingView = new LoadingView();
            $.ajax({
                url: '/search/catalog/internal/metacards/permanentlydelete',
                type: 'DELETE',
                data: JSON.stringify(ids),
                contentType: 'application/json'
            }).then(function(response){
                this.deleteResults();
                this.closeLightbox();
                announcement.announce({
                    title: '' + ids.length + ' Result(s) Deleted',
                    message: 'The result(s) were successfully deleted.',
                    type: 'success'
                });
            }.bind(this)).always(function(response) {
                setTimeout(function() { //let solr flush
                    loadingView.remove();
                }, 2000);
            }.bind(this));
        },
        getMetacardIds: function() {
            return this.model.map(function(result) {
                return result.get('metacard').get('id');
            });
        },
        fetchOrphans: function() {
            var self = this;
            LoadingCompanionView.beginLoading(self);
            var payload = JSON.stringify(this.getMetacardIds());
            $.ajax({
                url: '/search/catalog/internal/metacards/deletecheck',
                type: 'POST',
                data: payload,
                contentType: 'application/json',
                success: function(response, status, xhr) {
                    self.orphans = response['broken-links'];
                    self.render();
                    LoadingCompanionView.endLoading(self);
                },
                error: function() {
                    LoadingCompanionView.endLoading(self);
                    self.closeLightbox();
                    announcement.announce({
                        title: 'Unable to fetch associations before deleting',
                        message: 'Please try again later.',
                        type: 'error'
                    });
                }
            });
        },
        closeLightbox: function() {
            this.$el.trigger(CustomElements.getNamespace() + 'close-lightbox');
        },
        deleteResults: function() {
            ResultUtils.deleteResults(this.model.models);
        },
        refreshResults: function(){
            this.model.forEach(function(result){
                ResultUtils.refreshResult(result);
            });
        },
        serializeData: function() {
            return {
                orphans: this.orphans
            };
        }
    });
});