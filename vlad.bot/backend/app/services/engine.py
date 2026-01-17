import json
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from app.models import Session, Answer, Script, Bot
from sqlalchemy import select
from app.services.messaging import MessagingService

class FlowEngine:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.captured_messages = []

# ...

    async def send_message_to_user(self, session: Session, text: str, options: list = None):
        """
        Helper to send message via appropriate channel.
        """
        respondent = await self.db.get(Respondent, session.respondent_id) # Ensure loaded
        if not respondent:
            print("Error: No respondent linked to session.")
            return

        print(f"Server sending to {respondent.name} ({respondent.channel_type}): {text}")
        
        # Web / Preview Channel
        if respondent.channel_type == "web":
            msg = {"text": text, "sender": "bot"}
            if options:
                msg["options"] = options
            self.captured_messages.append(msg)
            return

        if respondent.channel_type == "telegram":
            # For options, we might want to append them to text or use proper buttons
            # For MVP simplicity: Append options to text
            if options:
                text += "\n\nВарианты:"
                for opt in options:
                     text += f"\n- {opt.get('label')}"
            
            await MessagingService.send_telegram(self.db, respondent.external_id, text)
            
        elif respondent.channel_type == "whatsapp":
             if options:
                text += "\n\nВарианты:"
                for opt in options:
                     text += f"\n- {opt.get('label')}"
             await MessagingService.send_whatsapp(self.db, respondent.external_id, text)

    async def get_next_node(self, script_data: dict, current_node_id: str | None, source_handle: str | None = None):
        """Finds the next node in the React Flow graph."""
        nodes = script_data.get("nodes", [])
        edges = script_data.get("edges", [])
        
        if not current_node_id:
            # Entry point: Look for 'start' node
            start_node = next((n for n in nodes if n["type"] == "start"), None)
            if start_node:
                return start_node
            # Fallback for old scripts
            return nodes[0] if nodes else None

        # Find specific edge if handle is provided (for conditions), otherwise any edge
        outgoing_edge = None
        
        if source_handle:
            outgoing_edge = next((e for e in edges if e["source"] == current_node_id and e.get("sourceHandle") == source_handle), None)
        else:
            # Default behavior (first edge found)
            outgoing_edge = next((e for e in edges if e["source"] == current_node_id), None)
            
        if not outgoing_edge:
            return None
            
        target_id = outgoing_edge["target"]
        return next((n for n in nodes if n["id"] == target_id), None)

    async def process_step(self, session: Session, user_input: str | None = None):
        """
        Main execution loop.
        """
        # Load Script (cache in real app)
        result = await self.db.execute(select(Script).where(Script.id == session.script_id))
        script = result.scalar_one()
        graph = script.graph_data

        current_node_id = session.current_node_id
        
        # 1. Handle Input for previous interactive node
        if session.state == "active" and current_node_id and user_input is not None:
             # Look up current node
             current_node = next((n for n in graph["nodes"] if n["id"] == current_node_id), None)
             
             # Handle Answer Saving
             if current_node and current_node["type"] in ["question", "single_choice"]:
                 # Save Answer
                 key = current_node["data"].get("variable", f"q_{current_node_id}")
                 
                 # Save to DB
                 answer = Answer(
                     session_id=session.id,
                     respondent_id=session.respondent_id,
                     node_id=current_node_id,
                     question_key=key,
                     value=user_input
                 )
                 self.db.add(answer)
                 
                 # Update Context Variable
                 variables = session.variables or {}
                 variables[key] = user_input
                 session.variables = variables
        
        # 2. Move Pointer (Initial or Next)
        if not current_node_id:
            # Start
            next_node = await self.get_next_node(graph, None)
        else:
            # Resume from interactive node
            current_node = next((n for n in graph["nodes"] if n["id"] == current_node_id), None)
            source_handle = None
            
            if current_node and current_node["type"] == "single_choice" and user_input is not None:
                # Determine which handle to follow based on user input (Option Label)
                options = current_node["data"].get("options", [])
                for idx, opt in enumerate(options):
                    # Compare label or value
                    if opt.get("label") == user_input:
                        # Try primary handle (Right)
                        handle_right = f"option-{idx}"
                        next_node = await self.get_next_node(graph, current_node_id, source_handle=handle_right)
                        
                        # Try secondary handle (Left)
                        if not next_node:
                            handle_left = f"option-{idx}-left"
                            next_node = await self.get_next_node(graph, current_node_id, source_handle=handle_left)
                        
                        if next_node:
                            await self.execute_node(session, next_node, graph)
                            return
                        
                        # If neither found, fallback to checking 'default' handle outside loop
                        source_handle = handle_right # Just to keep reference if needed
                        break
            
            # Fallback (if loop didn't return)
            # Try specific handle edge first (in case it was set but not found above, effectively redundant unless logic changes)
            next_node = None
            if source_handle:
                 next_node = await self.get_next_node(graph, current_node_id, source_handle=source_handle)

            # Fallback for single_choice: if specific option edge not found, try "default" handle
            if not next_node and current_node and current_node["type"] == "single_choice":
                 next_node = await self.get_next_node(graph, current_node_id, source_handle="default")
                 
            # Final fallback (any edge) is handled by get_next_node if handle is None, 
            # BUT if we passed a handle and it failed, we might want to try None?
            # Current get_next_node implementation: if source_handle is passed, it looks for THAT handle.
            # If we want fallback, we should call it again with None if first attempt failed.
            if not next_node and source_handle:
                next_node = await self.get_next_node(graph, current_node_id, source_handle=None)
        
        await self.execute_node(session, next_node, graph)

    async def execute_node(self, session: Session, node: dict | None, graph: dict):
        if not node:
            # End of flow
            session.state = "finished"
            session.finished_at = datetime.now()
            await self.db.commit()
            return

        session.current_node_id = node["id"]
        # Save state before side effects
        await self.db.commit() 
        
        node_type = node["type"]
        node_data = node["data"]
        
        if node_type == "message":
            # Send Message
            text = node_data.get("text", "")
            await self.send_message_to_user(session, text)
            
            # Check for interactive mode (Pause for "Next")
            if node_data.get("interactive"):
                # Stop and wait for user input
                return

            # Auto-proceed
            next_n = await self.get_next_node(graph, node["id"])
            await self.execute_node(session, next_n, graph)
            
        elif node_type in ["question", "single_choice"]:
            # Send Question
            text = node_data.get("text", "")
            options = node_data.get("options", []) if node_type == "single_choice" else None
            
            await self.send_message_to_user(session, text, options)
                
            # Stop and wait for user input
            return

        elif node_type == "condition":
            # Logic Evaluation
            variable_name = node_data.get("variable")
            operator = node_data.get("operator", "equals")
            check_value = node_data.get("value")
            
            # Get variable from session
            variables = session.variables or {}
            # Allow checking previous answer easily if variable not set? 
            # For now, strict: user must set variable in node settings
            actual_value = variables.get(variable_name)
            
            result = False
            
            # Simple Type Coercion (everything is string from DB mostly)
            # Try to convert to float for numeric comparisons
            
            def safe_float(v):
                try:
                    return float(v)
                except (ValueError, TypeError):
                    return v

            av_typed = safe_float(actual_value)
            cv_typed = safe_float(check_value)

            if operator == "equals":
                # Loose equality for strings "5" == 5
                result = str(actual_value) == str(check_value)
            elif operator == "not_equals":
                result = str(actual_value) != str(check_value)
            elif operator == "contains":
                result = str(check_value).lower() in str(actual_value).lower()
            elif operator == "gt":
                try: result = av_typed > cv_typed
                except: result = False
            elif operator == "lt":
                try: result = av_typed < cv_typed
                except: result = False
            
            print(f"Condition '{variable_name}' ({actual_value}) {operator} '{check_value}': {result}")
            
            # Follow True/False handle
            handle = "true" if result else "false"
            next_n = await self.get_next_node(graph, node["id"], source_handle=handle)
            await self.execute_node(session, next_n, graph)
