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
/*global require, setTimeout*/
var wreqr = require('wreqr');
var template = require('./map.hbs');
var Marionette = require('marionette');
var CustomElements = require('js/CustomElements');
var LoadingCompanionView = require('component/loading-companion/loading-companion.view');
var store = require('js/store');
var GeometryCollectionView = require('./geometry.collection.view');
var ClusterCollectionView = require('./cluster.collection.view');
var ClusterCollection = require('./cluster.collection');
var CQLUtils = require('js/CQLUtils');
var LocationModel = require('component/location-old/location-old');
var user = require('component/singletons/user-instance');

var MapModel = require('./map.model')
var MapInfoView = require('component/map-info/map-info.view')
var MapContextMenuDropdown = require('component/dropdown/map-context-menu/dropdown.map-context-menu.view')
var DropdownModel = require('component/dropdown/dropdown')
const MapHoverDropdown = require('component/dropdown/map-hover/dropdown.map-hover.view')

module.exports = Marionette.LayoutView.extend({
    tagName: CustomElements.register('map'),
    template: template,
    regions: {
        mapDrawingPopup: '#mapDrawingPopup',
        mapContextMenu: '.map-context-menu',
        mapHover: '.map-hover',
        mapInfo: '.mapInfo',
    },
    events: {
        'click .cluster-button': 'toggleClustering'
    },
    clusterCollection: undefined,
    clusterCollectionView: undefined,
    geometryCollectionView: undefined,
    map: undefined,
    mapModel: undefined,
    initialize: function(options) {
        if (!options.selectionInterface) {
            throw 'Selection interface has not been provided';
        }
        this.mapModel = new MapModel()
        this.listenTo(store.get('content'), 'change:drawing', this.handleDrawing);
        this.handleDrawing();
        this.setupMouseLeave();
    },
    setupMouseLeave: function() {
        this.$el.on('mouseleave', () => {
          this.mapModel.clearMouseCoordinates()
          this.mapHover.currentView.model.close()
        })
      },
    setupCollections: function() {
        if (!this.map) {
            throw 'Map has not been set.'
        }
        this.clusterCollection = new ClusterCollection();
        this.geometryCollectionView = new GeometryCollectionView({
            collection: this.options.selectionInterface.getActiveSearchResults(),
            map: this.map,
            selectionInterface: this.options.selectionInterface,
            clusterCollection: this.clusterCollection
        });
        this.clusterCollectionView = new ClusterCollectionView({
            collection: this.clusterCollection,
            map: this.map,
            selectionInterface: this.options.selectionInterface
        });
    },
    setupListeners: function() {
        this.listenTo(wreqr.vent, 'metacard:overlay', this.map.overlayImage.bind(this.map));
        this.listenTo(wreqr.vent, 'metacard:overlay:remove', this.map.removeOverlay.bind(this.map));
        this.listenTo(wreqr.vent, 'search:maprectanglefly', this.map.zoomToExtent.bind(this.map));
        this.listenTo(this.options.selectionInterface, 'reset:activeSearchResults', this.map.removeAllOverlays.bind(this.map));

        this.listenTo(this.options.selectionInterface.getSelectedResults(), 'update', this.map.zoomToSelected.bind(this.map));
        this.listenTo(this.options.selectionInterface.getSelectedResults(), 'add', this.map.zoomToSelected.bind(this.map));
        this.listenTo(this.options.selectionInterface.getSelectedResults(), 'remove', this.map.zoomToSelected.bind(this.map));

        this.listenTo(user.get('user').get('preferences'), 'change:resultFilter', this.handleCurrentQuery);
        this.listenTo(this.options.selectionInterface, 'change:currentQuery', this.handleCurrentQuery);
        this.handleCurrentQuery();

        if (this.options.selectionInterface.getSelectedResults()) {
            this.map.zoomToSelected(this.options.selectionInterface.getSelectedResults());
        }
        this.map.onMouseMove(this.onMapHover.bind(this));
        this.map.onRightClick(this.onRightClick.bind(this))
        this.setupRightClickMenu()
        this.setupMapHover()
        this.setupMapInfo()
    },
    onMapHover: function(event, mapEvent) {
        var metacard = this.options.selectionInterface
            .getActiveSearchResults()
            .get(mapEvent.mapTarget)
        this.updateTarget(event, metacard)
        this.$el.toggleClass(
            'is-hovering',
            Boolean(mapEvent.mapTarget && mapEvent.mapTarget !== 'userDrawing')
        )
        this.$el
            .find('.map-hover')
            .css('left', event.offsetX)
            .css('top', event.offsetY)
        this.$el.toggleClass('is-hovering', Boolean(mapEvent.mapTarget && mapEvent.mapTarget !== ('userDrawing')));
    },
    updateTarget: function(event, metacard) {
        var target
        var targetMetacard
        if (metacard) {
          target = metacard
            .get('metacard')
            .get('properties')
            .get('title')
          targetMetacard = metacard
          this.mapHover.currentView.model.open()
          this.mapHover.currentView.dropdownCompanion.updatePosition()
        } else {
          this.mapHover.currentView.model.close()
        }
        this.mapModel.set({
          target: target,
          targetMetacard: targetMetacard,
        })
      },
      onRightClick: function(event, mapEvent) {
        event.preventDefault()
        this.$el
          .find('.map-context-menu')
          .css('left', event.offsetX)
          .css('top', event.offsetY)
        this.mapModel.updateClickCoordinates()
        this.mapContextMenu.currentView.model.open()
      },
      setupMapHover() {
        this.mapHover.show(
          new MapHoverDropdown({
            model: new DropdownModel(),
            mapModel: this.mapModel,
            selectionInterface: this.options.selectionInterface,
            positionOffset: 30
          })
        )
      },
      setupRightClickMenu: function() {
        this.mapContextMenu.show(
          new MapContextMenuDropdown({
            model: new DropdownModel(),
            mapModel: this.mapModel,
            selectionInterface: this.options.selectionInterface,
            dropdownCompanionBehaviors: {
              navigation: {},
            },
          })
        )
      },
    setupMapInfo: function() {
        this.mapInfo.show(
          new MapInfoView({
            model: this.mapModel,
            selectionInterface: this.options.selectionInterface
          })
        )
      },
    /*
        Map creation is deferred to this method, so that all resources pertaining to the map can be loaded lazily and 
        not be included in the initial page payload.
        Because of this, make sure to return a deferred that will resolve when your respective map implementation 
        is finished loading / starting up.
        Also, make sure you resolve that deferred by passing the reference to the map implementation.
    */
    loadMap: function() {
        throw 'Map not implemented';
    },
    createMap: function(Map){
        this.map = Map(this.el.querySelector('#mapContainer'),
                this.options.selectionInterface, this.mapDrawingPopup.el, this.el,
                this.mapModel);
        this.setupCollections();
        this.setupListeners();
        this.endLoading();
    },
    initializeMap: function(){
        this.loadMap().then(function(Map) {
            this.createMap(Map);
        }.bind(this));
    },
    startLoading: function() {
        LoadingCompanionView.beginLoading(this);
    },
    endLoading: function() {
        LoadingCompanionView.endLoading(this);
    },
    onShow: function() {
        this.startLoading();
        setTimeout(function() {
            this.initializeMap();
        }.bind(this), 1000);
    },
    toggleClustering: function() {
        this.$el.toggleClass('is-clustering');
        this.clusterCollectionView.toggleActive();
    },
    handleDrawing: function() {
        this.$el.toggleClass('is-drawing', store.get('content').get('drawing'));
    },
    handleCurrentQuery: function() {
        this.removePreviousLocations();
        var currentQuery = this.options.selectionInterface.get('currentQuery');
        if (currentQuery) {
            this.handleFilter(CQLUtils.transformCQLToFilter(currentQuery.get('cql')), currentQuery.get('color'));
        }
        var resultFilter = user.get('user').get('preferences').get('resultFilter');
        if (resultFilter){
            this.handleFilter(CQLUtils.transformCQLToFilter(resultFilter), '#c89600');
        }
    },
    handleFilter: function(filter, color){
        if (filter.filters) {
            filter.filters.forEach(function(subfilter){
                this.handleFilter(subfilter, color);
            }.bind(this));
        } else {
            var pointText;
            var locationModel;
            switch (filter.type) {
                case 'DWITHIN':
                    if (CQLUtils.isPointRadiusFilter(filter)) {
                        pointText = filter.value.value.substring(6);
                        pointText = pointText.substring(0, pointText.length - 1);
                        var latLon = pointText.split(' ');
                        locationModel = new LocationModel({
                            lat: latLon[1],
                            lon: latLon[0],
                            radius: filter.distance,
                            color: color
                        });
                        this.map.showCircleShape(locationModel);
                    } else {
                        pointText = filter.value.value.substring(11);
                        pointText = pointText.substring(0, pointText.length - 1);
                        locationModel = new LocationModel({
                            lineWidth: filter.distance,
                            line: pointText.split(',').map(function(coordinate) {
                                return coordinate.split(' ').map(function(value) {
                                    return Number(value)
                                });
                            }),
                            color: color
                        });
                        this.map.showLineShape(locationModel);
                    }
                    break;
                case 'INTERSECTS':
                    pointText = filter.value.value.substring(9);
                    pointText = pointText.substring(0, pointText.length - 2);
                    pointText = pointText.split(',');
                    var points = pointText.map(function(pairText) {
                        return pairText.trim().split(' ').map(function(point) {
                            return Number(point);
                        });
                    });
                    locationModel = new LocationModel({
                        polygon: points,
                        color: color
                    });
                    this.map.showPolygonShape(locationModel);
                    break;
            }
        }
    },
    removePreviousLocations: function(){
        this.map.destroyShapes();
    },
    onDestroy: function() {
        if (this.geometryCollectionView){
            this.geometryCollectionView.destroy();
        }
        if (this.clusterCollectionView){
            this.clusterCollectionView.destroy();
        }
        if (this.clusterCollection){
            this.clusterCollection.reset();
        }
        if (this.map) {
            this.map.destroy();
        }
    }
});