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
var wreqr = require('wreqr');
var _ = require('underscore');
var Marionette = require('marionette');
var CustomElements = require('js/CustomElements');
var store = require('js/store');
var $ = require('jquery');
var metacardDefinitions = require('component/singletons/metacard-definitions');
var Common = require('js/Common');
var user = require('component/singletons/user-instance');
var properties = require('properties');
import * as React from 'react'
const HandleBarsHelpers = require('js/HandlebarsHelpers')

const Value = ({ value }) => {
    return value.map((subvalue) => {
        return (
            <span
                data-value={subvalue} title={subvalue}
            >
                {HandleBarsHelpers.ifUrl(subvalue) ?
                    <a href={subvalue} target="_blank">
                        {subvalue}
                    </a> :
                    subvalue
                }
            </span>
        )
    })
}

const Cell = ({ property, className, value }) => {
    return (
        <td
            data-property={property}
            className={className}
            data-value={value}
        >
            {property === 'thumbnail' ?
                <img src={Common.getImageSrc(Common.escapeHTML(value))} /> : <React.Fragment>
                <div>
                    <Value value={value} />
                </div>
                <div className="for-bold">
                    <Value value={value} />
                </div>
            </React.Fragment>}
        </td>
    )
}

module.exports = Marionette.ItemView.extend({
    className: 'is-tr',
    tagName: CustomElements.register('result-row'),
    attributes: function () {
        return {
            'data-resultid': this.model.id
        };
    },
    template(data) {
        const { properties } = data
        return (
            <React.Fragment>
                {properties.filter((property) => !property.hidden).map((property) => {
                    return (
                        <Cell
                            property={property.property}
                            className={property.class}
                            value={property.value}
                        />
                    )
                })}
            </React.Fragment>
        )
    },
    initialize: function (options) {
        if (!options.selectionInterface) {
            throw 'Selection interface has not been provided';
        }
        this.listenTo(this.model.get('metacard'), 'change:properties', this.render);
        this.listenTo(user.get('user').get('preferences'), 'change:columnHide', this.render);
        this.listenTo(user.get('user').get('preferences'), 'change:columnOrder', this.render);
        this.listenTo(this.options.selectionInterface.getSelectedResults(), 'update add remove reset', this.handleSelectionChange);
        this.handleSelectionChange();
    },
    handleSelectionChange: function () {
        var selectedResults = this.options.selectionInterface.getSelectedResults();
        var isSelected = selectedResults.get(this.model.id);
        this.$el.toggleClass('is-selected', Boolean(isSelected));
    },
    serializeData: function () {
        var prefs = user.get('user').get('preferences');
        var preferredHeader = user.get('user').get('preferences').get('columnOrder');
        var hiddenColumns = user.get('user').get('preferences').get('columnHide');
        var availableAttributes = this.options.selectionInterface.getActiveSearchResultsAttributes();
        var result = this.model.toJSON();
        return {
            id: result.id,
            properties: preferredHeader.filter(function (property) {
                return availableAttributes.indexOf(property) !== -1;
            }).map(function (property) {
                var value = result.metacard.properties[property];
                if (value === undefined) {
                    value = '';
                }
                if (value.constructor !== Array) {
                    value = [value];
                }
                var className = 'is-text';
                if (value && metacardDefinitions.metacardTypes[property]) {
                    switch (metacardDefinitions.metacardTypes[property].type) {
                        case 'DATE':
                            value = value.map(function (val) {
                                return val !== undefined && val !== '' ? Common.getHumanReadableDate(val) : '';
                            });
                            break;
                        default:
                            break;
                    }
                }
                if (property === 'thumbnail') {
                    className = "is-thumbnail";
                }
                return {
                    property: property,
                    value: value,
                    class: className,
                    hidden: hiddenColumns.indexOf(property) >= 0 || properties.isHidden(property) || metacardDefinitions.isHiddenTypeExceptThumbnail(property)
                };
            })
        };
    }
});
