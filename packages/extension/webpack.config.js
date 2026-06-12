const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');

module.exports = {
  entry: {
    content: './src/content.ts',
    background: './src/background/index.ts',
    popup: './src/popup.ts'
  },

  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: 'ts-loader',
        exclude: /node_modules/
      }
    ]
  },

  resolve: {
    extensions: ['.ts', '.js']
  },

  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: '[name].js',
    clean: true
  },

  plugins: [
    new CopyPlugin({
      patterns: [
        { from: 'public', to: '.' }
      ]
    })
  ]
};