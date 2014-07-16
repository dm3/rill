class Course < ActiveRecord::Base
  include Trashable, Activateable

  has_many :chapters, -> { order(:position) }
  has_many :questions, as: :questionable

  validates :name, presence: true, uniqueness: true

  default_scope { order(:name) }

  def self.current=(course)
    Thread.current[:course] = course
  end

  def self.current
    Thread.current[:course]
  end

  def to_s
    "#{name}"
  end

  def as_json
    {
      id: id,
      name: name,
      chapters: chapters.active.map(&:as_json),
      course_questions: questions.active.map(&:as_json)
    }
  end
end
