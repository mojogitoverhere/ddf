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

module.exports = Marionette.ItemView.extend({
    className: 'is-thead',
    template() {
        return (
            <React.Fragment>
                <th>
                    Source
                </th>
                <th data-help="This is the number of results available based on the current sorting.">
                    Available
                </th>
                <th data-help="This is the total number of results (hits) that matched your search.">
                    Possible
                </th>
                <th data-help="This is the time (in seconds) that it took for the search to run.">
                    Time (s)
                </th>
            </React.Fragment>
        )
    },
    tagName: CustomElements.register('query-status-header')
});
