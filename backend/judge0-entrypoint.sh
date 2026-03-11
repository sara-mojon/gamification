cat <<'EOF' > /usr/local/bin/isolate
clean_args=()
for arg in "$@"; do
  case "$arg" in
    --cg) ;;          # Si es --cg, lo tiramos a la basura
    --cg-*) ;;        # Si empieza por --cg-, lo tiramos a la basura
    *) clean_args+=("$arg") ;; # El resto lo guardamos
  esac
done
exec /usr/local/bin/isolate.real --no-cg "${clean_args[@]}"
EOF
chmod +x /usr/local/bin/isolate
chown judge0:judge0 /usr/local/bin/isolate
