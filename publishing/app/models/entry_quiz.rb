class EntryQuiz < ActiveRecord::Base

  belongs_to :course
  has_many :questions, as: :quizzable
  validates :course, presence: true

  scope :for_short_uuid, ->(id) { where(["SUBSTRING(CAST(id AS VARCHAR), 1, 8) = ?", id]) }
  def self.find_by_uuid(id, with_404 = true)
    entry_quizzes = for_short_uuid(id)
    raise ActiveRecord::RecordNotFound if entry_quizzes.empty? && with_404
    entry_quizzes.first
  end

  def to_param
    "#{id[0,8]}"
  end

  def to_s
    "Entry Quiz for #{course}"
  end

end
