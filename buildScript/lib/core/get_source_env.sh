if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="363f419392f51d8f70d8ecf6af2622a771cdcae3"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
