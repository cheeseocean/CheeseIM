{{- define "cheeseim.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "cheeseim.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "cheeseim.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "cheeseim.labels" -}}
app.kubernetes.io/name: {{ include "cheeseim.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{- define "cheeseim.selectorLabels" -}}
app.kubernetes.io/name: {{ include "cheeseim.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "cheeseim.serviceName" -}}
{{- printf "%s-%s" (include "cheeseim.fullname" .root) .service | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "cheeseim.image" -}}
{{- $registry := .root.Values.global.imageRegistry -}}
{{- $repository := required (printf "services.%s.image.repository is required" .service) .config.image.repository -}}
{{- $prefix := ternary (printf "%s/" (trimSuffix "/" $registry)) "" (ne $registry "") -}}
{{- if .config.image.digest -}}
{{- printf "%s%s@%s" $prefix $repository .config.image.digest -}}
{{- else -}}
{{- printf "%s%s:%s" $prefix $repository (required "global.imageTag is required when digest is empty" .root.Values.global.imageTag) -}}
{{- end -}}
{{- end -}}
