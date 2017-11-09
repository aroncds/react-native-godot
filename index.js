import { requireNativeComponent, ViewPropTypes, View } from 'react-native';

import PropTypes from 'prop-types';
var iface = {
  name: 'Godot',
  propTypes: {
    ...ViewPropTypes,
    package: PropTypes.string
  },
};

module.exports = requireNativeComponent('RCTGodotView', iface);
