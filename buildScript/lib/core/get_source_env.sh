if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="03df5df5b343c2d6da6bfd27656a12353cc457dd"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
