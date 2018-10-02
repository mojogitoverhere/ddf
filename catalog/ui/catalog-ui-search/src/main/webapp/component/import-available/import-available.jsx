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
import VirtualTable from "../virtual-table";
import * as React from "react";
import { hot } from "react-hot-loader";

const handleLocation = location => {
  if (location === undefined) {
    return (
      <div>
        <span className="fa fa-exclamation-triangle" />
        An error occurred fetching the import files.
      </div>
    );
  } else if (location === "") {
    return (
      <div>
        <span className="fa fa-exclamation-triangle" />
        The import directory is not configured. Please contact your
        administrator.
      </div>
    );
  } else {
    return (
      <div>
        Please place a file in the import folder to begin importing.
        <br />
        The import folder is located at {location}.
      </div>
    );
  }
};

const emptyRender = location => (
  <div className="is-large-font" style={{textAlign: 'center', height: '100%', padding: '10px'}}>
    {handleLocation(location)}
  </div>
);

const getRowClassName = (list, selectionInterface, { index }) => {
  const rowData = list[index];
  if (!rowData) {
    return "";
  }
  const selected =
    selectionInterface
      .get("selected")
      .findWhere({ id: rowData.id }) !== undefined;
  return selected ? "is-selected" : "";
};

function render(props) {
  const {
    list,
    onRowClick,
    selectionInterface,
    location,
    onImport,
    onRefresh
  } = props;
  const hasFilesSelected = selectionInterface.get("selected").length !== 0
  return (
    <React.Fragment>
      <div className="title-container">
        <div className="import-files-title">File(s) to Import</div>
        <span className="refresh fa fa-refresh is-button" onClick={onRefresh} />
      </div>
      <div className="import-file-list">
        <VirtualTable
          list={list}
          columns={[
            {
              label: "File Name",
              dataKey: "path",
              columnWidth: 0.5
            },
            {
              label: "File Size",
              dataKey: "size",
              columnWidth: 0.5
            }
          ]}
          emptyRender={emptyRender.bind(null, location)}
          onRowClick={onRowClick}
          rowClassName={getRowClassName.bind(null, list, selectionInterface)}
        />
      </div>
      <div>
        <button
          className="is-positive import-button"
          data-help="Import"
          onClick={onImport}
                  disabled={!hasFilesSelected}
                  title={hasFilesSelected ? '' : 'Select files to import first.'}
        >
          <span className="mainText">Import</span>
        </button>
      </div>
    </React.Fragment>
  );
}
export default hot(module)(render);
