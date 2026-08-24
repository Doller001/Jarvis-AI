"""
Custom exception hierarchy for Jarvis AI Backend.
"""

class JarvisBaseException(Exception):
    """Base exception for all Jarvis application errors."""
    def __init__(self, message: str, code: str = "INTERNAL_ERROR"):
        super().__init__(message)
        self.message = message
        self.code = code


class AuthenticationError(JarvisBaseException):
    def __init__(self, message: str = "Authentication failed"):
        super().__init__(message, code="AUTHENTICATION_ERROR")


class ProviderUnavailableError(JarvisBaseException):
    def __init__(self, provider: str, message: str | None = None):
        msg = message or f"LLM Provider '{provider}' is currently unavailable."
        super().__init__(msg, code="PROVIDER_UNAVAILABLE")
        self.provider = provider


class ModelUnavailableError(JarvisBaseException):
    def __init__(self, model_id: str, message: str | None = None):
        msg = message or f"Requested model '{model_id}' is not available."
        super().__init__(msg, code="MODEL_UNAVAILABLE")
        self.model_id = model_id


class ValidationError(JarvisBaseException):
    def __init__(self, message: str):
        super().__init__(message, code="VALIDATION_ERROR")


class PermissionDeniedError(JarvisBaseException):
    def __init__(self, message: str = "Permission denied for target tool"):
        super().__init__(message, code="PERMISSION_DENIED")


class ConfirmationRequiredError(JarvisBaseException):
    def __init__(self, action: str, confirmation_token: str, prompt: str):
        super().__init__(f"Action '{action}' requires user confirmation.", code="CONFIRMATION_REQUIRED")
        self.action = action
        self.confirmation_token = confirmation_token
        self.prompt = prompt


class ToolExecutionError(JarvisBaseException):
    def __init__(self, tool_name: str, message: str):
        super().__init__(f"Error executing tool '{tool_name}': {message}", code="TOOL_EXECUTION_ERROR")
        self.tool_name = tool_name
