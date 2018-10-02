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
import * as React from 'react'
var Marionette = require('marionette');
var CustomElements = require('js/CustomElements');

const topColumn = (hasReturned, successful, messages) => {
    if (hasReturned) {
        if (successful) {
            return ''
        } else {
            return <span className="fa fa-warning" title={messages}></span>
        }
    } else {
        return ''
    }
}

const possibleColumn = (hasReturned, successful, hits, messages) => {
    if (hasReturned) {
        if (successful) {
            return hits
        } else {
            return <span className="fa fa-warning" title={messages}></span>
        }
    } else {
        return <span className="fa fa-circle-o-notch fa-spin" title="Waiting for source to return"></span>
    }
}

const timeColumn = (hasReturned, successful, elapsed, messages) => {
    if (hasReturned) {
        if (successful) {
            return elapsed
        } else {
            return <span className="fa fa-warning" title={messages}></span>
        }
    } else {
        return <span className="fa fa-circle-o-notch fa-spin" title="Waiting for source to return"></span>
    }
}

module.exports = Marionette.ItemView.extend({
    className: 'is-tr',
    tagName: CustomElements.register('query-status-row'),
    template(data) {
        const { id, hasReturned, successful, anyHasReturned, anyHasNotReturned, elapsed, hits, messages, top } = data
        return (
            <React.Fragment>
                <td>
                    {id}
                    {topColumn(hasReturned, successful, messages)}
                </td>
                <td>
                    {anyHasReturned ? top : ''}
                    {topColumn(hasReturned, successful, messages)}
                    {anyHasNotReturned ? <span className="fa fa-circle-o-notch fa-spin" title="Waiting for source to return"></span> : ''}
                </td>
                <td>
                    {possibleColumn(hasReturned, successful, hits, messages)}
                </td>
                <td>
                    {timeColumn(hasReturned, successful, elapsed, messages)}
                </td>
            </React.Fragment>
        )
    },
    modelEvents: {
        'change': 'render'
    },
    serializeData: function () {
        var modelJSON = this.model.toJSON();
        modelJSON.fromremote = modelJSON.top - modelJSON.fromcache;
        modelJSON.elapsed = modelJSON.elapsed / 1000;
        modelJSON.anyHasReturned = modelJSON.hasReturned || modelJSON.cacheHasReturned;
        modelJSON.anyHasNotReturned = !modelJSON.hasReturned || !modelJSON.cacheHasReturned;
        return modelJSON;
    }
});
