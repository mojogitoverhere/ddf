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
/*global define, window*/
define([
    'wreqr',
    'marionette',
    'underscore',
    'jquery',
    './metacard-interactions.hbs',
    'js/CustomElements',
    'js/store',
    'js/Download',
    'component/router/router',
    'component/singletons/user-instance',
    'component/singletons/sources-instance',
    'decorator/menu-navigation.decorator',
    'decorator/Decorators',
    'component/lightbox/lightbox.view.instance',
    'component/metacard-delete/metacard-delete.view',
    'component/metacard-offline/metacard-offline.view',
    'component/metacard-export/metacard-export.view',
    'component/loading/loading.view',
    'js/ResultUtils',
    'component/announcement',
    'js/jquery.whenAll'
], function (wreqr, Marionette, _, $, template, CustomElements, store, Download, router, user, sources, MenuNavigationDecorator, Decorators, lightboxInstance, MetacardDeleteView, MetacardOfflineView, MetacardExportView, LoadingView, ResultUtils, announcement) {

    return Marionette.ItemView.extend(Decorators.decorate({
        template: template,
        tagName: CustomElements.register('metacard-interactions'),
        modelEvents: {
            'change': 'render'
        },
        events: {
            'click .interaction-save': 'handleSave',
            'click .interaction-unsave': 'handleUnsave',
            'click .interaction-hide': 'handleHide',
            'click .interaction-show': 'handleShow',
            'click .interaction-expand': 'handleExpand',
            'click .interaction-share': 'handleShare',
            'click .interaction-download': 'handleDownload',
            'click .interaction-export': 'handleExport',
            'click .interaction-offline': 'handleOffline',
            'click .interaction-archive': 'handleArchive',
            'click .interaction-archive-restore': 'handleRestore',
            'click .interaction-delete': 'handleDelete',
            'click .metacard-interaction': 'handleClick'
        },
        ui: {
        },
        initialize: function(){
            var currentWorkspace = store.getCurrentWorkspace();
            if (currentWorkspace) {
                this.listenTo(currentWorkspace, 'change:metacards', this.checkIfSaved);
            }
            this.listenTo(this.model, 'refreshdata', this.render)
            this.listenTo(user.get('user').get('preferences').get('resultBlacklist'), 
                'add remove update reset', this.checkIfBlacklisted);
            this.$el.toggleClass('is-restore-allowed', user.get('user').get('isRestoreAllowed') === true);
            this.$el.toggleClass('is-perm-delete-allowed', user.get('user').get('isPermDeleteAllowed') === true);
            this.$el.toggleClass('is-offline-allowed', user.get('user').get('isOfflineAllowed') === true);

            var allMetacardsAreOffline = this.model.every(entry => entry.isOfflined());
            this.$el.toggleClass('is-download-enabled', allMetacardsAreOffline === false);
        },
        onRender: function(){
            this.checkTypes();
            this.checkIfSaved();
            this.checkIsInWorkspace();
            this.checkIfMultiple();
            this.checkIfRouted();
            this.checkIfBlacklisted();
        },
        handleSave: function(){
            var currentWorkspace = store.getCurrentWorkspace();
            if (currentWorkspace){
                var ids = this.model.map(function(result){
                    return result.get('metacard').get('properties').get('id');
                });
                currentWorkspace.set('metacards', _.union(currentWorkspace.get('metacards'), ids));
            } else {
                //bring up modal to select workspace(s) to save to
            }
            this.checkIfSaved();
        },
        handleUnsave: function(){
            var currentWorkspace = store.getCurrentWorkspace();
            if (currentWorkspace){
                var ids = this.model.map(function(result){
                    return result.get('metacard').get('properties').get('id');
                });
                currentWorkspace.set('metacards', _.difference(currentWorkspace.get('metacards'), ids));
            }
            this.checkIfSaved();
        },
        handleHide: function(){
            var preferences = user.get('user').get('preferences');
            preferences.get('resultBlacklist').add(this.model.map(function(result){
                return {
                    id: result.get('metacard').get('properties').get('id'),
                    title: result.get('metacard').get('properties').get('title')
                };
            }));
            preferences.savePreferences();
        },
        handleShow: function(){
            var preferences = user.get('user').get('preferences');
            preferences.get('resultBlacklist').remove(this.model.map(function(result){
                return result.get('metacard').get('properties').get('id');
            }));
            preferences.savePreferences();
        },
        handleExpand: function(){
            var id = this.model.first().get('metacard').get('properties').get('id');
            wreqr.vent.trigger('router:navigate', {
                fragment: 'metacards/'+id,
                options: {
                    trigger: true
                }
            });
        },
        handleShare: function(){
            
        },
        handleDownload: function() {
            var self = this;
            this.model.forEach(function(result) {
                self.doDirectDownload(result);
            });
        },
        doDirectDownload: function(result) {
            var url = result.get('metacard').get('properties').get('resource-download-url');
            // This filename is will only be honored if the response from the URL does not contain
            // a Content-Disposition header with the "filename" attribute
            var filename = result.get('metacard').get('properties').get('title');
            Download.fromUrl(filename, url);
        },
        handleOffline: function() {
            lightboxInstance.model.updateTitle('Move to Offline Archive');
            lightboxInstance.model.open();
            lightboxInstance.lightboxContent.show(new MetacardOfflineView({
                model: this.model
            }));
        },
        handleExport: function() {
            lightboxInstance.model.updateTitle('Export');
            lightboxInstance.model.open();
            lightboxInstance.lightboxContent.show(new MetacardExportView({model: this.model}));
        },
        handleArchive: function() {
            lightboxInstance.model.updateTitle('Delete');
            lightboxInstance.model.open();
            lightboxInstance.lightboxContent.show(new MetacardDeleteView({
                model: this.model
            }));
        },
        handleDelete: function() {
            lightboxInstance.model.updateTitle('Delete Permanently');
            lightboxInstance.model.open();
            lightboxInstance.lightboxContent.show(new MetacardDeleteView({
                model: this.model,
                permanent: true
            }));
        },
        handleRestore: function() {
            var loadingView = new LoadingView();
            $.whenAll.apply(this, this.model.map(function(result) {
                return $.get('/search/catalog/internal/history/' +
                    'revert/' +
                    result.get('metacard').get('properties').get('metacard.deleted.id') +
                    '/' +
                    result.get('metacard').get('properties').get('metacard.deleted.version')).then(function(response) {
                        ResultUtils.deleteResults(this.model.models);
                }.bind(this));
            }.bind(this))).always(function(response) {
                setTimeout(function() { //let solr flush
                    loadingView.remove();
                }, 2000);
            });
        },
        handleClick: function(){
            this.$el.trigger('closeDropdown.'+CustomElements.getNamespace());
        },
        checkIfSaved: function(){
            var currentWorkspace = store.getCurrentWorkspace();
            if (currentWorkspace){
                var ids = this.model.map(function(result){
                    return result.get('metacard').get('properties').get('id');
                });
                var isSaved = true;
                ids.forEach(function(id){
                    if (currentWorkspace.get('metacards').indexOf(id) === -1){
                        isSaved = false;
                    }
                });
                this.$el.toggleClass('is-saved', isSaved);
            }
        },
        checkIsInWorkspace: function(){
            var currentWorkspace = store.getCurrentWorkspace();
            this.$el.toggleClass('in-workspace', Boolean(currentWorkspace));
        },
        checkIfMultiple: function(){
            this.$el.toggleClass('is-multiple', Boolean(this.model.length > 1));
        },
        checkIfRouted: function(){
            this.$el.toggleClass('is-routed', Boolean(router.toJSON().name === 'openMetacard'));
        },
        checkIfBlacklisted: function(){
            var pref = user.get('user').get('preferences');
            var blacklist = pref.get('resultBlacklist');
            var ids = this.model.map(function(result){
                return result.get('metacard').get('properties').get('id');
            });
            var isBlacklisted = false;
            ids.forEach(function(id){
                if (blacklist.get(id) !== undefined){
                    isBlacklisted = true;
                }
            });
            this.$el.toggleClass('is-blacklisted', isBlacklisted);
        },
        checkTypes: function(){
            var types = {};
            this.model.forEach(function(result){
                if (result.isWorkspace()){
                    types.workspace = true;
                } else if (result.isResource()){
                    types.resource = true;
                } else if (result.isRevision()){
                    types.revision = true;
                } else if (result.isDeleted()){
                    types.deleted = true;
                }

                if (result.isRemote()){
                    types.remote = true;
                }
                if (result.isOfflined()){
                    types.offlined = true;
                }
            });
            this.$el.toggleClass('is-mixed', Object.keys(types).length > 1);
            this.$el.toggleClass('is-workspace', types.workspace !== undefined);
            this.$el.toggleClass('is-resource', types.resource !== undefined);
            this.$el.toggleClass('is-revision', types.revision !== undefined);
            this.$el.toggleClass('is-deleted', types.deleted !== undefined);
            this.$el.toggleClass('is-offlined', types.offlined !== undefined);
            this.$el.toggleClass('is-remote', types.remote !== undefined);
        },
        serializeData: function(){
            var currentWorkspace = store.getCurrentWorkspace();
            var resultJSON, workspaceJSON;
            if (this.model){
                resultJSON = this.model.toJSON()
            }
            if (currentWorkspace){
                workspaceJSON = currentWorkspace.toJSON()
            }
            var result = resultJSON[0];
            return {
                remoteResourceCached: result.isResourceLocal && result.metacard.properties['source-id'] !== sources.localCatalog,
                result: resultJSON,
                workspace: workspaceJSON
            }
        }
    }, MenuNavigationDecorator));
});
