"""Fábrica/registro de adaptadores: resuelve la implementación a partir de un tipo de proveedor validado.

Sin ``if``/``elif`` por proveedor: los tipos se registran (por configuración ``MCP_PROVIDER_ADAPTERS`` o con
``register``). Agregar un proveedor = una clase nueva + una entrada; ni ``AIService`` ni las tools cambian.
"""

from __future__ import annotations

import importlib
import re
from collections.abc import Callable, Mapping

from aplicafacil_mcp.domain.errors import UnknownProviderTypeError
from aplicafacil_mcp.domain.models import ProviderCapabilities
from aplicafacil_mcp.providers.base import AdapterOptions, AIProvider

ProviderConstructor = Callable[[AdapterOptions], AIProvider]

_TYPE_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]{1,31}$")


class ProviderFactory:
    def __init__(self, options: AdapterOptions | None = None) -> None:
        self._options = options or AdapterOptions()
        self._constructors: dict[str, ProviderConstructor] = {}

    @classmethod
    def from_paths(cls, adapters: Mapping[str, str], options: AdapterOptions | None = None) -> ProviderFactory:
        """``{"OPENAI": "paquete.modulo:Clase"}``. Importa cada clase y verifica que implemente ``AIProvider``."""
        factory = cls(options)
        for provider_type, target in adapters.items():
            module_name, _, class_name = target.partition(":")
            adapter_class = getattr(importlib.import_module(module_name), class_name)
            factory.register(provider_type, adapter_class)
        return factory

    def register(self, provider_type: str, constructor: type[AIProvider] | ProviderConstructor) -> None:
        if not _TYPE_PATTERN.match(provider_type):
            raise ValueError(f"Tipo de proveedor inválido: {provider_type!r}")
        if isinstance(constructor, type):
            if not issubclass(constructor, AIProvider):
                raise TypeError(f"{constructor!r} no implementa AIProvider")
            if constructor.provider_type != provider_type:
                raise ValueError(f"{constructor.__name__} declara {constructor.provider_type}, no {provider_type}")
        self._constructors[provider_type] = constructor

    def supported_types(self) -> list[str]:
        return sorted(self._constructors)

    def supports(self, provider_type: str) -> bool:
        return provider_type in self._constructors

    def capabilities(self, provider_type: str) -> ProviderCapabilities:
        constructor = self._constructor(provider_type)
        if isinstance(constructor, type):
            return constructor.capabilities
        # Constructor arbitrario: crear el adaptador no llama a la red (contrato, punto 2)
        return self.create(provider_type).capabilities

    def create(self, provider_type: str) -> AIProvider:
        provider = self._constructor(provider_type)(self._options)
        if not isinstance(provider, AIProvider):
            raise TypeError(f"El constructor de {provider_type} no devolvió un AIProvider")
        return provider

    def _constructor(self, provider_type: str) -> ProviderConstructor:
        try:
            return self._constructors[provider_type]
        except KeyError:
            raise UnknownProviderTypeError(f"Tipo de proveedor no soportado: {provider_type}") from None
