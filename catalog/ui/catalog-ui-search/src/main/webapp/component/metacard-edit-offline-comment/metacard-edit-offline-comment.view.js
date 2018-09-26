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
    './metacard-edit-offline-comment.hbs',
    'js/CustomElements',
    'component/loading/loading.view',
    'js/ResultUtils'
], function(Marionette, _, $, template, CustomElements, LoadingView, ResultUtils) {

    return Marionette.ItemView.extend({
        template: template,
        tagName: CustomElements.register('metacard-edit-offline-comment'),
        events: {
            'click button.edit-offline-comment-confirm': 'triggerSave',
            'click button.edit-offline-comment-cancel': 'closeLightbox'
        },
        initialize: function(options) {
            this.selectionInterface = options.selectionInterface || this.selectionInterface;
            if (!options.model) {
                this.model = this.selectionInterface.getSelectedResults();
            }
        },
        triggerSave: function() {
            var ids = this.model.filter(function(result) {
                return result.isOfflined();
            }).map(function(result) {
                return result.get('metacard').get('id');
            });
            var offlineComment = $("#comment").val();

            var payload = [
                   {
                       ids: ids,
                       attributes: [
                            {
                                attribute: "ext.offline-comment",
                                values: [offlineComment]
                            }
                       ]
                   }
            ];

            var loadingView = new LoadingView();
            var self = this;
            setTimeout(function(){
                $.ajax({
                    url: '/search/catalog/internal/metacards',
                    type: 'PATCH',
                    data: JSON.stringify(payload),
                    contentType: 'application/json'
                }).then(function(response){
                    self.refreshResults();
                }).always(function(){
                    setTimeout(function(){
                        loadingView.remove();
                        self.closeLightbox();
                    }, 1000);
                })
               }, 1000);
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