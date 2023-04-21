if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="a4eacbd0e54b6ec0a42096c42b6137a5be91a0bc"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
