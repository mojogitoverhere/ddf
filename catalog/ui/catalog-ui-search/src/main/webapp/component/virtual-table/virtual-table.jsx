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
import { Column, Table, AutoSizer, CellMeasurer, CellMeasurerCache } from 'react-virtualized';
import 'react-virtualized/styles.css'; // only needs to be imported once
import * as React from 'react'
const _ = require('underscore')
const $ = require('jquery')
const Positioning = require('js/Positioning')
import { hot } from 'react-hot-loader'

const DefaultColumnStyle = {
  whiteSpace: 'inherit',
}

const DefaultColumnRender = ({ value, rowData }) => (
  <div
    className="cell-container"
    style={{
      wordBreak: 'break-all'
    }}>
    {value}
  </div>
)

const getDefaultEmptyRender = (emptyText) => {
  return () => (
    <div className="is-large-font" style={{textAlign: 'center', height: '100%', padding: '10px'}}>
        {emptyText ? emptyText : 'Nothing to show.'}
    </div>
  )
}

class VirtualTable extends React.PureComponent {
  tableRef = React.createRef()
  spanRef = React.createRef()
  autoSizeRef = React.createRef()
  _cache = new CellMeasurerCache({
    fixedWidth: true,
    defaultHeight: 20
  })
  _getColumnCellRenderer = (ColumnRender) => {
    return ({ columnIndex, dataKey, parent, rowIndex }) => {
      const { list } = this.props;
      const rowData = list[rowIndex]
      const value = rowData[dataKey]
      return (
        <CellMeasurer
          cache={this._cache}
          columnIndex={columnIndex}
          parent={parent}
          rowIndex={rowIndex}>
          {ColumnRender ? <ColumnRender value={value} rowData={rowData} /> : <DefaultColumnRender value={value} rowData={rowData} />}
        </CellMeasurer>
      );
    }
  }
  handleResize = _.debounce(() => {
    if (this.tableRef.current && !Positioning.isEffectivelyHidden(this.spanRef.current)) {
      this._cache.clearAll();
      this.tableRef.current.recomputeRowHeights();
      this.autoSizeRef.current._onResize(); 
    }
  }, 1000)
  componentDidMount = () => {
    $(window).on('resize', this.handleResize);
  }
  componentWillUnmount = () => {
    $(window).off('resize', this.handleResize);
  }
  _rowGetter = ({ index }) => {
    const { list } = this.props;

    return list.get(index % list.size);
  }
  componentWillReceiveProps = () => {
    if (this.tableRef.current) {
      this.tableRef.current.forceUpdateGrid();
    }
  }
  getRowClassName = ({ index }) => {
    const { rowClassName } = this.props
    let className = 'virtual-table-row'
    if (rowClassName && typeof rowClassName === 'function') {
      className += ` ${rowClassName({ index })}`
    } else if (rowClassName) {
      className += ` ${rowClassName}`
    }
    return className;
  }
  render() {
    const { list, columns, emptyRender, emptyText, onRowClick } = this.props
    return (
      <React.Fragment>
        <AutoSizer ref={this.autoSizeRef}>
          {({ width, height }) => {
            if (this._lastRenderedWidth !== width) {
              this._lastRenderedWidth = width;
              this._cache.clearAll();
            }
            return (<Table
              ref={this.tableRef}
              deferredMeasurementCache={this._cache}
              width={width}
              height={height}
              headerHeight={44}
              rowHeight={this._cache.rowHeight}
              rowCount={list.length}
              rowGetter={({ index }) => list[index]}
              gridClassName="is-list"
              rowClassName={this.getRowClassName}
              noRowsRenderer={emptyRender || getDefaultEmptyRender(emptyText)}
              onRowClick={onRowClick}
              tabIndex={-1}
            >
              {columns.map(({ label, dataKey, columnWidth, columnRender, columnStyle }) => (
                <Column
                  key={label}
                  label={label}
                  dataKey={dataKey}
                  width={columnWidth * width}
                  cellRenderer={this._getColumnCellRenderer(columnRender)}
                  style={{
                    ...DefaultColumnStyle,
                    ...columnStyle
                  }}
                  className="virtual-table-cell"
                />
              ))}
            </Table>)
          }
        }
      </AutoSizer>
        <span ref={this.spanRef}></span>
        </React.Fragment>
    )
  }
}

export default hot(module)(VirtualTable)