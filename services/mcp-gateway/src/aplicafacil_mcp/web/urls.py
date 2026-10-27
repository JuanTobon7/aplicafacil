from django.urls import path

from aplicafacil_mcp.web import views

urlpatterns = [
    path("", views.index),
    path("health/live", views.live),
    path("health/ready", views.ready),
    path("metrics", views.metrics),
]
