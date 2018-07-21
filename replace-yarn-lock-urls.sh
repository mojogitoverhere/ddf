#!/bin/bash
find . -name yarn.lock -exec sed -i -e "s#https://registry.yarnpkg.com#http://nexus3.phx.connexta.com/repository/npmjs.org#g" {} \;
