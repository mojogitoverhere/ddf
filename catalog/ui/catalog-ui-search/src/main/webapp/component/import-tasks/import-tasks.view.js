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
var TableView = require('component/table/table.view');
var HeaderView = require('./header/import-tasks-header.view');
var BodyView = require('./body/import-tasks-body.view');

module.exports = TableView.extend({
    className: 'import-tasks',
    getHeaderView: function(){
        return new HeaderView();
    },
    getBodyView: function(){
        return new BodyView({
            collection: this.model.get('tasks')
        });
    }
});