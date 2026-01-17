"""add script title

Revision ID: add_script_title
Revises: 
Create Date: 2026-01-17

"""
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision = 'add_script_title'
down_revision = None
branch_labels = None
depends_on = None

def upgrade():
    op.add_column('scripts', sa.Column('title', sa.String(), nullable=True))

def downgrade():
    op.drop_column('scripts', 'title')
