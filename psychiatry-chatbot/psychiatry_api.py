import os
from flask import Flask, request, jsonify
from dotenv import load_dotenv
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_core.prompts import PromptTemplate
from langchain_community.vectorstores import FAISS
try:
    from langchain_classic.chains import create_retrieval_chain
    from langchain_classic.chains.combine_documents import create_stuff_documents_chain
except ImportError:
    from langchain.chains import create_retrieval_chain
    from langchain.chains.combine_documents import create_stuff_documents_chain
from connect_memory_with_llm import SimpleHFChatModel, REPO_ID, DB_FAISS_PATH, set_custom_prompt

# Load environment variables
load_dotenv()
HF_TOKEN = os.getenv("HF_TOKEN")

app = Flask(__name__)

# Initialize components
print("Loading vector database and embedding model...")
embedding_model = HuggingFaceEmbeddings(model_name="sentence-transformers/all-MiniLM-L6-v2")
vectorstore = FAISS.load_local(DB_FAISS_PATH, embedding_model, allow_dangerous_deserialization=True)

print("Initializing LangChain HF Chat Model...")
chat_model = SimpleHFChatModel(
    repo_id=REPO_ID,
    hf_token=HF_TOKEN,
    temperature=0.5,
    max_new_tokens=512
)

# Set up QA Chain
qa_prompt = set_custom_prompt()
combine_docs_chain = create_stuff_documents_chain(chat_model, qa_prompt)
qa_chain = create_retrieval_chain(
    retriever=vectorstore.as_retriever(search_kwargs={'k': 4}), 
    combine_docs_chain=combine_docs_chain
)
print("Psychiatry RAG pipeline initialized successfully.")

@app.route('/chat', methods=['POST'])
def chat():
    data = request.json or {}
    user_input = data.get('message', '')
    if not user_input:
        return jsonify({'error': 'Message is required'}), 400
        
    try:
        print(f"Received request: {user_input}")
        response = qa_chain.invoke({"input": user_input})
        result = response["answer"]
        print(f"Generated response: {result}")
        return jsonify({'response': result})
    except Exception as e:
        print(f"Error serving chat request: {e}")
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    print("Starting Psychiatry Chatbot Flask Server on port 5001...")
    app.run(host='0.0.0.0', port=5001)
