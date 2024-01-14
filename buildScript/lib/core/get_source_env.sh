if [ ! -z $ENV_NB4A ]; then
  export COMMIT_SING_BOX_EXTRA="472d5f40e4052e0b5e3918a7f35303edb682958c"
fi

if [ ! -z $ENV_SING_BOX_EXTRA ]; then
  source libs/get_source_env.sh
  # export COMMIT_SING_BOX="91495e813068294aae506fdd769437c41dd8d3a3"
fi
