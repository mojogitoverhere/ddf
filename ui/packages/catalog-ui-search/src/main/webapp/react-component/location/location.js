const React = require('react')

const CustomElements = require('../../js/CustomElements.js')

const Button = require('../button')
const Dropdown = require('../dropdown')
const Json = require('../json')
const { Menu, MenuItem } = require('../menu')
import styled from '../styles/styled-components/styled-components'
const {
  validateInput,
} = require('../../component/location-new/utils/dms-utils')

const Line = require('./line')
const Polygon = require('./polygon')
const PointRadius = require('./point-radius')
const BoundingBox = require('./bounding-box')
const Keyword = require('./keyword')
const plugin = require('plugins/location')

const readableNames = {
  lat: 'latitude',
  lon: 'longitude',
  west: 'longitude',
  east: 'longitude',
  north: 'latitude',
  south: 'latitude',
}

const validLatLon = {
  lat: '90',
  lon: '180',
  west: '180',
  east: '180',
  north: '90',
  south: '90',
  dmsLat: '90°00\'00"',
  dmsLon: '180°00\'00"',
}

const inputs = plugin({
  line: {
    label: 'Line',
    Component: Line,
  },
  poly: {
    label: 'Polygon',
    Component: Polygon,
  },
  circle: {
    label: 'Point-Radius',
    Component: PointRadius,
  },
  bbox: {
    label: 'Bounding Box',
    Component: BoundingBox,
  },
  keyword: {
    label: 'Keyword',
    Component: ({ setState, keywordValue, ...props }) => {
      return (
        <Keyword
          {...props}
          value={keywordValue}
          setState={({ value, ...data }) => {
            setState({ keywordValue: value, ...data })
          }}
        />
      )
    },
  },
})

const drawTypes = ['line', 'poly', 'circle', 'bbox']

const Form = ({ children }) => (
  <div className="form-group clearfix">{children}</div>
)

const DrawButton = ({ onDraw }) => (
  <Button className="location-draw is-primary" onClick={onDraw}>
    <span className="fa fa-globe" />
    <span>Draw</span>
  </Button>
)

const DropdownPadding = styled.div`
  padding-bottom: ${props => props.theme.minimumSpacing};
`
const Invalid = styled.div`
  background-color: ${props => props.theme.negativeColor};
  height: 100%;
  display: block;
  overflow: hidden;
  color: white;
`

const Component = CustomElements.registerReact('location')
var errors = false
var inValidInput = ''
var inValidKey = ''
var defaultCoord = ''
const LocationInput = props => {
  const { mode, setState, cursor } = props
  const input = inputs[mode] || {}
  const { Component: Input = null } = input
  return (
    <Component>
      <Json value={props} onChange={value => setState(value)} />
      <DropdownPadding>
        <Dropdown label={input.label || 'Select Location Option'}>
          <Menu value={mode} onChange={cursor('mode')}>
            {Object.keys(inputs).map(key => (
              <MenuItem key={key} value={key}>
                {inputs[key].label}
              </MenuItem>
            ))}
          </Menu>
        </Dropdown>
      </DropdownPadding>
      <Form>
        {Input !== null ? <Input {...props} /> : null}
        {errors ? (
          <Invalid>
            &nbsp;
            <span className="fa fa-exclamation-triangle" />
            &nbsp; {inValidInput} is not an acceptable {inValidKey} value.
            Defaulting to {defaultCoord}.
          </Invalid>
        ) : (
          ''
        )}
        {drawTypes.includes(mode) ? <DrawButton onDraw={props.onDraw} /> : null}
      </Form>
    </Component>
  )
}

const ddValidators = {
  lat: value => value <= 90 && value >= -90,
  lon: value => value <= 180 && value >= -180,
  north: value => value <= 90 && value >= -90,
  west: value => value <= 180 && value >= -180,
  south: value => value <= 90 && value >= -90,
  east: value => value <= 180 && value >= -180,
}

var isDms = false
const dmsValidators = {
  dmsLat: value => validateInput(value, 'dd°mm\'ss.s"'),
  dmsLon: value => validateInput(value, 'ddd°mm\'ss.s"'),
  dmsNorth: value => validateInput(value, 'dd°mm\'ss.s"'),
  dmsSouth: value => validateInput(value, 'dd°mm\'ss.s"'),
  dmsWest: value => validateInput(value, 'ddd°mm\'ss.s"'),
  dmsEast: value => validateInput(value, 'ddd°mm\'ss.s"'),
}

const getNegOrPosLatLon = (key, value) => {
  if (value < 0) {
    return -1 * validLatLon[key]
  } else {
    return validLatLon[key]
  }
}

module.exports = ({ state, setState, options }) => (
  <LocationInput
    {...state}
    onDraw={options.onDraw}
    setState={setState}
    cursor={key => value => {
      isDms = false
      let validateCoords = ddValidators[key]
      if (validateCoords === undefined) {
        validateCoords = dmsValidators[key]
        isDms = true
      }
      if (!isDms) {
        if (typeof validateCoords === 'function' && !validateCoords(value)) {
          errors = true
          inValidInput = value
          inValidKey = readableNames[key]
          defaultCoord = getNegOrPosLatLon(key, value)
          value = defaultCoord
          setState(key, value)
          return
        }
        setState(key, value, (errors = false))
      } else {
        if (
          typeof validateCoords === 'function' &&
          validateCoords(value) !== value &&
          value !== ''
        ) {
          errors = true
          inValidInput = value
          inValidKey = readableNames[key]
          defaultCoord = validateCoords(value)
          value = defaultCoord
          setState(key, value)
          return
        }
        setState(key, value, (errors = false))
      }
    }}
  />
)
