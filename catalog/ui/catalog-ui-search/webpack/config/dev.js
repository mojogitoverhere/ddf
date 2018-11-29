var path = require('path');
var webpack = require('webpack');
var merge = require('webpack-merge');

var base = require('./base');

var resolve = function (place) {
    return path.resolve(__dirname, '../../', place)
};

module.exports = merge.smart(base, {
    devServer: {
        watchOptions: {
            ignored: /node_modules/,
            poll: 1000,
        },
        progress: true,
        historyApiFallback: true,
        inline: true,
        hot: true,
        contentBase: [resolve('./node_modules/cesium/Build/'), resolve('/src/main/webapp/')],
        proxy: {
            '/search/catalog/**': {
                target: 'https://localhost:8993',
                secure: false,
                changeOrigin: true
            },
            '/services/**': {
                target: 'https://localhost:8993',
                secure: false,
                changeOrigin: true
            }
        }
    },
    entry: [
        'stack-source-map/register',
        'console-polyfill'
    ],
    plugins: [
        new webpack.NoErrorsPlugin(),
        new webpack.NamedModulesPlugin(),
        new webpack.HotModuleReplacementPlugin()
    ]
});