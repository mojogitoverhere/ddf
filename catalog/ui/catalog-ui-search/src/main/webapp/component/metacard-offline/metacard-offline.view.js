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
    './metacard-offline.hbs',
    'js/CustomElements',
    'component/loading/loading.view',
    'js/ResultUtils'
], function(Marionette, _, $, template, CustomElements, LoadingView, ResultUtils) {

    return Marionette.ItemView.extend({
        template: template,
        tagName: CustomElements.register('metacard-offline'),
        events: {
            'click button.offline-confirm': 'triggerOffline',
            'click button.offline-cancel': 'closeLightbox'
        },
        initialize: function(options) {
            this.selectionInterface = options.selectionInterface || this.selectionInterface;
            if (!options.model) {
                this.model = this.selectionInterface.getSelectedResults();
            }
        },
        triggerOffline: function() {
            var ids = this.model.map(function(result) {
                return result.get('metacard').get('id');
            });
            var offlineComment = $("#comment").val();
            var payload = JSON.stringify({"ids":ids, "comment": offlineComment});
            var loadingView = new LoadingView();

            $.ajax({
                url: '/search/catalog/internal/resource/offline',
                type: 'POST',
                data: payload,
                contentType: 'application/json'
            }).always(function(response) {
                this.refreshResults();
                loadingView.remove();
                this.closeLightbox();
            }.bind(this));
        },
        refreshResults: function(){
            this.model.forEach(function(result){
                ResultUtils.refreshResult(result);
            });
        },
        closeLightbox: function(){
         this.$el.trigger(CustomElements.getNamespace() + 'close-lightbox');
        }
    });
});