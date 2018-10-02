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
import VirtualTable from '../virtual-table'
import * as React from 'react'
import { hot } from 'react-hot-loader'

const Status = ({ value, rowData }) => (
    <div>
        {value}
        {rowData.info ? <span data-info={rowData.info} className="info fa fa-info-circle is-button" /> : ''}
    </div>
)

function render(props) {
    const { list, showCompleted, onShowCompletedChange, location } = props;
    return <React.Fragment>
        <div className="title-container">
            <div className="export-status-title">
                Export Status
                            </div>
            <div className="export-location">
                {location ? ` (Configured export directory: ${location})` : ''}
            </div>
            <div className="completed-exports">
                <label className="showCompleted">
                    Show completed exports
                    <input className="showCompleted"
                    type="checkbox"
                    checked={showCompleted}
                    onChange={onShowCompletedChange} />
                </label>
            </div>
        </div>
        <div className="tasks-container">
            <VirtualTable list={showCompleted ? list : list.filter(task => task.state !== 'Complete')} columns={[
                {
                    label: 'Title',
                    dataKey: 'title',
                    columnWidth: .20
                },
                {
                    label: 'File Name',
                    dataKey: 'file',
                    columnWidth: .20
                },
                {
                    label: 'Start Time',
                    dataKey: 'started',
                    columnWidth: .20
                },
                {
                    label: 'Status',
                    dataKey: 'state',
                    columnWidth: .20,
                    columnRender: Status
                }, 
                {
                    label: 'Progress',
                    dataKey: 'progress',
                    columnWidth: .20
                }
            ]} emptyText="No exports have been started." />
        </div>
    </React.Fragment>
}
export default hot(module)(render)