package srdc.smartcds.model.llm

case class LoginRequest(
                         client_id: String,
                         client_secret: String
                       )

case class LoginResponse(
                          access_token: String,
                          token_type: String,
                          session_id: String
                        )
