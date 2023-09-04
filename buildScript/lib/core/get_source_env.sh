if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="2d7e05f22f2c3285ca929aaa0cad79ba4fd3e1fb"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
